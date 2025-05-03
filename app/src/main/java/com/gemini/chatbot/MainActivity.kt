package com.gemini.chatbot

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.StringWriter

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
        val modelBytes = assets.open("qa_model.onnx").readBytes()
        ortSession = ortEnv.createSession(modelBytes)

        labelMapping = loadLabelMapping()

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

    private fun loadLabelMapping(): Map<String, String> {
        val inputStream = assets.open("label_mapping.json")
        val writer = StringWriter()
        val buffer = CharArray(1024)
        inputStream.use { input ->
            val reader = BufferedReader(InputStreamReader(input, "UTF-8"))
            var n = reader.read(buffer)
            while (n != -1) {
                writer.write(buffer, 0, n)
                n = reader.read(buffer)
            }
        }
        val jsonString = writer.toString()
        val obj = JSONObject(jsonString)
        val map = mutableMapOf<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = obj.getString(key)
        }
        return map
    }
}

object OnnxCustomTensor {
    fun createTensor(env: OrtEnvironment, strings: Array<String>): OnnxTensor {
        val shape = longArrayOf(strings.size.toLong())
        return OnnxTensor.createTensor(env, strings, shape)
    }
}