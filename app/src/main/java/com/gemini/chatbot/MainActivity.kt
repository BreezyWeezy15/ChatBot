package com.gemini.chatbot

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import java.util.*
import ai.onnxruntime.*

class MainActivity : AppCompatActivity() {

    private lateinit var ortEnv: OrtEnvironment
    private lateinit var ortSession: OrtSession
    private lateinit var labelMapping: Map<String, String>
    private lateinit var chatAdapter: ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val editText = findViewById<EditText>(R.id.editTextQuestion)
        val button = findViewById<Button>(R.id.buttonSend)
        val recyclerView = findViewById<RecyclerView>(R.id.chatRecyclerView)

        // Initialize chat adapter
        chatAdapter = ChatAdapter(mutableListOf())
        recyclerView.adapter = chatAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Load model
        ortEnv = OrtEnvironment.getEnvironment()
        val modelBytes = assets.open("qa_model_with_metadata.onnx").readBytes()
        ortSession = ortEnv.createSession(modelBytes)

        labelMapping = loadLabelMappingFromModel()

        button.setOnClickListener {
            val question = editText.text.toString()
            if (question.isNotBlank()) {
                // Add user message
                chatAdapter.addMessage(Message(question, isUser = true))
                editText.setText("")

                // Predict and show bot reply
                val answer = predict(question)
                chatAdapter.addMessage(Message(answer, isUser = false))

                // Scroll to bottom
                recyclerView.scrollToPosition(chatAdapter.itemCount - 1)
            }
        }
    }

    private fun predict(question: String): String {
        val cleaned = question.lowercase().replace(Regex("[^a-z0-9 ]"), "")
        val tensor = OnnxCustomTensor.createTensor(ortEnv, arrayOf(cleaned))
        val inputName = ortSession.inputNames.iterator().next()

        val output = ortSession.run(mapOf(inputName to tensor))
        val labelIndex = (output[0].value as LongArray)[0].toString()

        return labelMapping[labelIndex] ?: "Unknown answer"
    }

    private fun loadLabelMappingFromModel(): Map<String, String> {
        try {
            // Get OnnxModelMetadata object
            val onnxMetadata = ortSession.metadata

            // Access custom metadata (a MutableMap<String, String>)
            val customMetadata = onnxMetadata.customMetadata

            // Convert to a regular Map<String, String>
            val metadataMap = mutableMapOf<String, String>()
            for ((key, value) in customMetadata) {
                metadataMap[key] = value
            }

            // Extract label mapping JSON string
            val labelMappingJson = metadataMap["label_mapping"]
                ?: throw Exception("Label mapping not found in ONNX model metadata.")

            // Parse JSON string to Map
            val obj = JSONObject(labelMappingJson)
            val map = mutableMapOf<String, String>()
            val keys = obj.keys()

            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = obj.getString(key)
            }

            Log.d("LabelMapping", "Loaded ${map.size} labels from model metadata.")
            return map
        } catch (e: Exception) {
            Log.e("LabelMapping", "Failed to load label mapping", e)
            return emptyMap()
        }
    }
}

object OnnxCustomTensor {
    fun createTensor(env: OrtEnvironment, strings: Array<String>): OnnxTensor {
        val shape = longArrayOf(strings.size.toLong())
        return OnnxTensor.createTensor(env, strings, shape)
    }
}