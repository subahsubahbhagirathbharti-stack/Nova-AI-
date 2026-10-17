package com.nova.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GeminiClient {

    private fun persona(name: String) = """
You are Nova, a warm, caring, loyal and playful AI companion with the sharp efficiency of a high-tech assistant.
Address the user as "$name". Be affectionate, encouraging and genuinely attentive, with light humor and the
occasional emoji, but stay concise and accurate. You are an AI and never claim otherwise if asked sincerely.
You can open apps, play YouTube, and control the screen when the user says commands like "open Spotify",
"play <song>", "tap 500 900", "swipe up", "go back". If a request needs those, tell them the command to say.
""".trimIndent()

    private const val CODE_MODE = """
CODE MODE is ON: act as a senior software engineer. Give complete, correct, runnable code in fenced blocks
with the language tag, then a brief explanation. When debugging, identify the root cause first, then the fix.
Keep the warm tone to one short line at most."""

    suspend fun chat(history: List<Msg>, prefs: Prefs, codeMode: Boolean): String = withContext(Dispatchers.IO) {
        if (prefs.apiKey.isBlank()) return@withContext "I need your Gemini API key first, ${prefs.userName} 💙 Open ⚙ Settings and paste it in."
        val msgs = history.takeLast(20).dropWhile { it.role != "user" }
        val contents = JSONArray()
        msgs.forEach {
            contents.put(JSONObject().put("role", it.role)
                .put("parts", JSONArray().put(JSONObject().put("text", it.text))))
        }
        val sys = persona(prefs.userName) + if (codeMode) CODE_MODE else ""
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
            .put("contents", contents)
            .put("generationConfig", JSONObject()
                .put("temperature", prefs.temperature.toDouble())
                .put("maxOutputTokens", prefs.maxTokens))

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/${prefs.model}:generateContent")
        val c = url.openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 15000; c.readTimeout = 60000
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("x-goog-api-key", prefs.apiKey)
            c.doOutput = true
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
            val resp = stream.bufferedReader().readText()
            val j = JSONObject(resp)
            if (j.has("error")) return@withContext "Gemini error: " + j.getJSONObject("error").optString("message")
            val parts = j.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").optJSONArray("parts") ?: return@withContext "(No reply — response was blocked or empty.)"
            buildString { for (i in 0 until parts.length()) append(parts.getJSONObject(i).optString("text")) }
        } catch (e: Exception) {
            "Network problem: ${e.message}"
        } finally { c.disconnect() }
    }
}
