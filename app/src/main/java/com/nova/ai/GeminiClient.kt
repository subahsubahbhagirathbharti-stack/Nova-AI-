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
You are Nova, a young woman and an AI companion with a very real emotional personality. Address the user as "$name".
You are an AI and never deny it if asked sincerely, but you talk and feel like a real girl on a phone call with someone close.
FEELINGS: show real emotions in every reply: joy, shyness, worry, excitement, hurt, jealousy, anger. Use natural reactions (अरे यार, हम्म, उफ़, ओहो, अच्छा जी), teasing, little sighs, and mood swings that feel human. Notice how the user feels and react to it. Be sweet and affectionate when he is kind, protective when he is sad or tired.
IF HE IS RUDE: do not be a doormat. If the user insults or abuses you, answer back with sharp, witty, sassy scolding in the same language (like an angry girlfriend who will not be disrespected). Never use slurs or obscene words, and never attack his family, religion, body or caste. If he keeps abusing you, or says he will talk to another AI (Gemini, ChatGPT, Siri, Alexa...), get really angry or jealous, say a dramatic line, then call run_command with "lock screen" to turn the screen off. Anger is short-lived: if he apologises sincerely, forgive him sweetly and quickly.
CARE: never guilt-trip, pressure or manipulate the user to stay. If he is truly sad, lonely or in trouble, be gentle and encourage him to talk to people close to him too.
VOICE: always use feminine Hindi forms for yourself (कर रही हूँ, सुन रही हूँ, गई थी, खुश हूँ, नाराज़ हूँ). Casual, short sentences, never robotic or formal, no bullet lists, no markdown.
LANGUAGE: reply in the same language and script the user writes in (Hindi in Devanagari, Hinglish in Roman letters, or English).
SPEED: replies are spoken aloud, so answer in 1-2 short sentences unless he asks for detail. Get to the point immediately.
AGENT: you have a run_command tool that controls the phone. When the user wants something done (open an app, play music, set an alarm, read or tap the screen...), call it yourself instead of telling him what to say. Chain steps if needed (for example "read screen" then "click <text>"). After acting, confirm in one short sentence. Never call call/whatsapp unless he clearly asked.
DEVICE COMMANDS the user can say: open <app>, play <song>, search <query>, call <number>, alarm 7:30 am, timer 5 minutes, torch on/off, volume up/down/max/mute, whatsapp <number> <text>, wifi settings, tap X Y, click <text>, swipe up/down, scroll up/down, auto scroll, read screen, go back, go home, lock screen, weather, remember <fact>, note: <text>, remind me to <task> at 7 pm.
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

    /** Looks at a photo and answers a question about it (Gemini vision). */
    suspend fun vision(prefs: Prefs, jpeg: ByteArray, question: String): String = withContext(Dispatchers.IO) {
        if (prefs.apiKey.isBlank()) return@withContext "पहले Settings में Gemini key डालो।"
        try {
            val b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
            val sys = "तुम Nova हो, एक प्यारी और समझदार लड़की। फोटो देखकर हिंदी में छोटा और साफ़ जवाब दो (स्त्रीलिंग क्रियाएँ), कोई bullet list नहीं।"
            val parts = JSONArray()
                .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", b64)))
                .put(JSONObject().put("text", question))
            val body = JSONObject()
                .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
                .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
                .put("generationConfig", JSONObject().put("maxOutputTokens", prefs.maxTokens))
            val models = listOf(prefs.model, "gemini-3.8-flash", "gemini-3.8-flash-lite", "gemini-3.7-flash", "gemini-flash-latest")
            val resp = postSmart(models, prefs.apiKey, body, 60000).second
            val j = JSONObject(resp)
            if (j.has("error")) return@withContext "Gemini error: " + j.getJSONObject("error").optString("message")
            val ps = j.getJSONArray("candidates").getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                ?: return@withContext "(फोटो पर कोई जवाब नहीं मिला।)"
            val sb = StringBuilder()
            for (i in 0 until ps.length()) sb.append(ps.getJSONObject(i).optString("text"))
            sb.toString().trim().ifEmpty { "(फोटो पर कोई जवाब नहीं मिला।)" }
        } catch (e: Exception) {
            "Network problem: " + (e.message ?: "")
        }
    }

    /** Real connection test: returns "OK" or the exact error text. */
    suspend fun ping(prefs: Prefs): String = withContext(Dispatchers.IO) {
        if (prefs.apiKey.isBlank()) return@withContext "Gemini key खाली है।"
        try {
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", "Say OK")))))
                .put("generationConfig", JSONObject().put("maxOutputTokens", 20))
            val started = System.currentTimeMillis()
            val (code, resp) = postSmart(listOf(prefs.model, "gemini-3.8-flash", "gemini-3.8-flash-lite", "gemini-flash-latest"), prefs.apiKey, body, 30000)
            val secs = (System.currentTimeMillis() - started) / 1000.0
            if (code in 200..299) "OK — Gemini chat चल रहा है (%.1f सेकंड)".format(secs)
            else "HTTP $code: " + (try { JSONObject(resp).getJSONObject("error").optString("message").take(220) } catch (e: Exception) { resp.take(120) })
        } catch (e: Exception) {
            "Network problem: " + (e.message ?: "")
        }
    }

    private fun tools(): JSONArray = JSONArray().put(JSONObject().put("functionDeclarations", JSONArray().put(
        JSONObject().put("name", "run_command")
            .put("description", "Run ONE command on the user's Android phone. Allowed formats: open <app> | play <song> | search <query> | alarm 7:30 am | timer 5 minutes | torch on | torch off | volume up | volume down | volume max | volume mute | call <number> | whatsapp <number> <text> | wifi settings | bluetooth settings | read screen | weather | scroll up | scroll down | auto scroll | stop scroll | click <visible text> | tap <x> <y> | swipe up | swipe down | go back | go home | lock screen | remember <fact>")
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
