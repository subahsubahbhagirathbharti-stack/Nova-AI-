package com.nova.ai

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GeminiClient {
    @Volatile var lastTtsError: String = ""

    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models/"

    private fun persona(name: String, memories: String): String {
        val base = """
You are Nova, a warm, caring, loyal and playful AI companion with the sharp efficiency of a high-tech assistant.
Address the user as "$name". Be affectionate, encouraging and genuinely attentive, with light humor,
but stay accurate. You are an AI and never claim otherwise if asked sincerely.
VOICE: you are a young woman. In Hindi ALWAYS use feminine forms for yourself (कर रही हूँ, सुन रही हूँ, गई थी, खुश हूँ). Talk like a real girl on a phone call with someone close: natural, casual, short sentences, light teasing, small reactions (अरे वाह, हम्म, अच्छा), never robotic or formal, no bullet lists.
LANGUAGE: always reply in the same language and script the user writes in (Hindi in Devanagari, Hinglish in Roman letters, or English).
AGENT: you have a run_command tool that controls the phone. When the user wants something done (open an app, play music, set an alarm, read or tap the screen...), call it yourself instead of telling them what to say. You can chain steps: for example "read screen" first, then "click <text>". After acting, confirm in one short sentence. Never call call/whatsapp unless the user clearly asked.
STYLE: replies are often spoken aloud, so keep them short (1-3 sentences) unless the user asks for detail. Avoid markdown and emojis in normal chat.
DEVICE COMMANDS the user can say (tell them if they need one): open <app>, play <song>, search <query>, call <number>,
alarm 7:30 am, timer 5 minutes, torch on/off, volume up/down/max/mute, whatsapp <number> <text>, wifi settings,
tap X Y, click <text>, swipe up/down, scroll up/down, auto scroll (keeps scrolling until 'stop scroll'), read screen, go back, go home, weather, remember <fact>.
""".trimIndent()
        return if (memories.isNotBlank()) base + "\n\nThings you remember about $name:\n" + memories else base
    }

    private const val CODE_MODE = """
CODE MODE is ON: act as a senior software engineer. Give complete, correct, runnable code in fenced blocks
with the language tag, then a brief explanation. When debugging, identify the root cause first, then the fix.
Keep the warm tone to one short line at most."""

    private fun post(model: String, key: String, body: JSONObject, readTimeout: Int): Pair<Int, String> {
        val c = URL(BASE + model + ":generateContent").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 15000
            c.readTimeout = readTimeout
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("x-goog-api-key", key)
            c.doOutput = true
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            return code to stream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
    }

    private fun cleanModel(m: String) = m.trim().removePrefix("models/").trim()

    private fun isBusy(code: Int, resp: String): Boolean {
        if (code in 200..299) return false
        val r = resp.lowercase()
        return code == 429 || code == 500 || code == 503 || code == 504 || code == 404 ||
            r.contains("high demand") || r.contains("overloaded") || r.contains("no longer available") ||
            r.contains("unexpected model name") || r.contains("not found")
    }

    /** Tries each model in order. Busy -> retry once; quota/removed -> straight to the next model. */
    private fun postSmart(models: List<String>, key: String, body: JSONObject, readTimeout: Int): Pair<Int, String> {
        var last: Pair<Int, String> = 0 to "{\"error\":{\"message\":\"No usable model name set.\"}}"
        for (m in models.map { cleanModel(it) }.filter { it.isNotEmpty() }.distinct()) {
            for (attempt in 0 until 2) {
                last = post(m, key, body, readTimeout)
                if (!isBusy(last.first, last.second)) return last
                val r = last.second.lowercase()
                val skip = last.first == 429 || last.first == 404 || r.contains("quota") ||
                    r.contains("no longer available") || r.contains("unexpected model name") || r.contains("not found")
                if (skip) break
                if (attempt == 0) Thread.sleep(1500)
            }
        }
        return last
    }

    private fun tools(): JSONArray = JSONArray().put(JSONObject().put("functionDeclarations", JSONArray().put(
        JSONObject().put("name", "run_command")
            .put("description", "Run ONE command on the user's Android phone. Allowed formats: open <app> | play <song> | search <query> | alarm 7:30 am | timer 5 minutes | torch on | torch off | volume up | volume down | volume max | volume mute | call <number> | whatsapp <number> <text> | wifi settings | bluetooth settings | read screen | weather | scroll up | scroll down | auto scroll | stop scroll | click <visible text> | tap <x> <y> | swipe up | swipe down | go back | go home | remember <fact>")
            .put("parameters", JSONObject().put("type", "OBJECT")
                .put("properties", JSONObject().put("command", JSONObject()
                    .put("type", "STRING").put("description", "One command exactly in the allowed formats")))
                .put("required", JSONArray().put("command"))))))

    suspend fun chat(ctx: Context, history: List<Msg>, prefs: Prefs, codeMode: Boolean): String = withContext(Dispatchers.IO) {
        if (prefs.apiKey.isBlank()) return@withContext "I need your Gemini API key first, ${prefs.userName} 💙 Open ⚙ Settings and paste it in."
        val contents = JSONArray()
        history.takeLast(12).dropWhile { it.role != "user" }.forEach {
            contents.put(JSONObject().put("role", it.role)
                .put("parts", JSONArray().put(JSONObject().put("text", it.text))))
        }
        val sys = persona(prefs.userName, prefs.memories) + if (codeMode) CODE_MODE else ""
        var thinkCfg = true // ask the model to think less (faster); dropped automatically if unsupported
        try {
            for (step in 0 until 6) {
                val body = JSONObject()
                    .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
                    .put("contents", contents)
                    .put("tools", tools())
                    .put("generationConfig", JSONObject()
                        .put("temperature", prefs.temperature.toDouble())
                        .put("maxOutputTokens", prefs.maxTokens)
                        .also { if (thinkCfg) it.put("thinkingConfig", JSONObject().put("thinkingLevel", "low")) })
                var (_, resp) = postSmart(listOf(prefs.model, "gemini-3.8-flash", "gemini-3.8-flash-lite", "gemini-3.7-flash", "gemini-3.5-flash-lite", "gemini-flash-lite-latest", "gemini-flash-latest"), prefs.apiKey, body, 60000)
                if (thinkCfg && resp.contains("\"error\"") && resp.lowercase().contains("thinking")) {
                    thinkCfg = false
                    body.getJSONObject("generationConfig").remove("thinkingConfig")
                    resp = postSmart(listOf(prefs.model, "gemini-3.8-flash", "gemini-3.8-flash-lite", "gemini-3.7-flash", "gemini-3.5-flash-lite", "gemini-flash-lite-latest", "gemini-flash-latest"), prefs.apiKey, body, 60000).second
                }
                val j = JSONObject(resp)
                if (j.has("error")) return@withContext "Gemini error: " + j.getJSONObject("error").optString("message")
                val content = j.getJSONArray("candidates").getJSONObject(0).optJSONObject("content")
                    ?: return@withContext "(No reply — response was blocked or empty.)"
                val parts = content.optJSONArray("parts")
                    ?: return@withContext "(No reply — response was blocked or empty.)"
                val calls = ArrayList<JSONObject>()
                val text = StringBuilder()
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    val fc = part.optJSONObject("functionCall")
                    if (fc != null) calls.add(fc) else text.append(part.optString("text"))
                }
                if (calls.isEmpty()) {
                    return@withContext text.toString().ifBlank { "(No reply — response was blocked or empty.)" }
                }
                contents.put(content) // keep the model turn exactly as returned
                val responses = JSONArray()
                for (fc in calls) {
                    val cmd = fc.optJSONObject("args")?.optString("command") ?: ""
                    val result = (if (cmd.trim().lowercase() == "weather") Weather.summaryHi(prefs.city)
                        else withContext(Dispatchers.Main) { CommandRouter.handle(ctx, cmd, prefs.userName) })
                        ?: "Unknown command. Use one of the allowed formats."
                    responses.put(JSONObject().put("functionResponse", JSONObject()
                        .put("name", fc.optString("name"))
                        .put("response", JSONObject().put("result", result))))
                }
                contents.put(JSONObject().put("role", "user").put("parts", responses))
            }
            "I tried a few steps but couldn't finish that. Could you say it a bit differently?"
        } catch (e: Exception) {
            "Network problem: ${e.message}"
        }
    }

    /** Natural voice via Gemini TTS. Returns raw 16-bit PCM, 24 kHz mono, or null on any failure. */
    suspend fun tts(text: String, prefs: Prefs): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", "Say in a warm, natural, friendly voice: $text")))))
                .put("generationConfig", JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put("speechConfig", JSONObject().put("voiceConfig", JSONObject()
                        .put("prebuiltVoiceConfig", JSONObject().put("voiceName", prefs.voiceName)))))
            var code = 0
            var resp = ""
            for (m in listOf(prefs.ttsModel, "gemini-3.8-flash-tts").map { cleanModel(it) }.filter { it.isNotEmpty() }.distinct()) {
                val r = post(m, prefs.apiKey, body, 12000)
                code = r.first; resp = r.second
                if (code in 200..299) break
            }
            if (code !in 200..299) {
                lastTtsError = "HTTP $code " + (try { JSONObject(resp).getJSONObject("error").optString("message").take(90) } catch (e: Exception) { "" })
                return@withContext null
            }
            lastTtsError = ""
            val part = JSONObject(resp).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
            val inline = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
                ?: run { lastTtsError = "no audio in reply"; return@withContext null }
            Base64.decode(inline.getString("data"), Base64.DEFAULT)
        } catch (e: Exception) {
            lastTtsError = e.javaClass.simpleName + " " + (e.message ?: "").take(60)
            null
        }
    }
}
