package com.jarvis.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Jarvis ka AI dimaag (Gemini).
 * Use: val reply = brain.ask("Free Fire laga do")   // suspend fun
 * Error / internet na ho to exception aayegi -> purana keyword tareeka chalao.
 */
class AiBrain(
    private val tools: Tools,
    private val launcher: AppLauncher
) {
    companion object {
        // Model naam yahan badal sakte ho (AI Studio mein dekh lena)
        const val MODEL = "gemini-flash-latest"
        private const val MAX_STEPS = 5
        private const val MAX_HISTORY = 10
    }

    // Sirf final user/model text yaad rakhte hain (chhoti memory)
    private val history = mutableListOf<JSONObject>()

    private fun systemPrompt(): String {
        val apps = launcher.installedApps().joinToString(", ") { it.label }
        return """
            Tum Jarvis ho, ek phone voice assistant. User Hindi, Hinglish ya English mein bolta hai.
            Jawab chhote rakho (1-2 vaakya), kyunki jawab bola jayega. Markdown ya emoji mat use karo.
            Normal baat ho to seedha jawab do. Phone ka kaam ho to sahi tool chalao.
            Agar kaam unclear ho (kaunsa contact, kitne baje) to ek chhota sawaal poochho.
            App kholte waqt neeche di list se app ka naam bilkul waise hi bhejo.
            Installed apps: $apps
        """.trimIndent()
    }

    suspend fun ask(userText: String): String = withContext(Dispatchers.IO) {
        val contents = JSONArray()
        history.forEach { contents.put(it) }
        val userMsg = msg("user", userText)
        contents.put(userMsg)

        val system = systemPrompt()
        repeat(MAX_STEPS) {
            val resp = post(contents, system)
            val cand = resp.optJSONArray("candidates")?.optJSONObject(0)
                ?: throw IOException("Khaali jawab aaya")
            val content = cand.optJSONObject("content")
                ?: throw IOException("Jawab block ho gaya")
            val parts = content.optJSONArray("parts") ?: JSONArray()

            // Model ka content jaisa aaya waisa hi wapas bhejte hain (thought signatures ke liye)
            contents.put(content)

            val calls = mutableListOf<JSONObject>()
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val p = parts.getJSONObject(i)
                if (p.has("functionCall")) calls.add(p.getJSONObject("functionCall"))
                else if (p.has("text")) sb.append(p.getString("text"))
            }

            if (calls.isEmpty()) {
                val answer = sb.toString().trim().ifEmpty { "Ho gaya." }
                history.add(userMsg)
                history.add(msg("model", answer))
                while (history.size > MAX_HISTORY) history.removeAt(0)
                return@withContext answer
            }

            val results = JSONArray()
            for (fc in calls) {
                val name = fc.getString("name")
                val args = fc.optJSONObject("args") ?: JSONObject()
                val result = try {
                    tools.run(name, args)
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
                results.put(
                    JSONObject().put(
                        "functionResponse",
                        JSONObject().put("name", name)
                            .put("response", JSONObject().put("result", result))
                    )
                )
            }
            contents.put(JSONObject().put("role", "user").put("parts", results))
        }
        "Kaam poora nahi ho paya, dobara bolo."
    }

    private fun msg(role: String, text: String) = JSONObject()
        .put("role", role)
        .put("parts", JSONArray().put(JSONObject().put("text", text)))

    private fun post(contents: JSONArray, system: String): JSONObject {
        val body = JSONObject()
            .put(
                "system_instruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system)))
            )
            .put("contents", contents)
            .put(
                "tools",
                JSONArray().put(JSONObject().put("functionDeclarations", tools.declarations()))
            )

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IOException("API error $code: ${text.take(200)}")
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }
}
