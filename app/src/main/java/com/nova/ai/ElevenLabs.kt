package com.nova.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ElevenLabs {
    /** Returns MP3 bytes, or null on any failure (wrong key/voice, no credits, offline). */
    suspend fun tts(text: String, prefs: Prefs): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.elevenlabs.io/v1/text-to-speech/" + prefs.elevenVoiceId.trim() + "?output_format=mp3_44100_128"
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"
                c.connectTimeout = 15000
                c.readTimeout = 60000
                c.setRequestProperty("xi-api-key", prefs.elevenKey.trim())
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("Accept", "audio/mpeg")
                c.doOutput = true
                val body = JSONObject().put("text", text).put("model_id", prefs.elevenModel.trim())
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                if (c.responseCode in 200..299) c.inputStream.readBytes() else null
            } finally {
                c.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }
}
