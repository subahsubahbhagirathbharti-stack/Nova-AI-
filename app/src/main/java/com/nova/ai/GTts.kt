package com.nova.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Free Google Translate voice (the same one the Python gTTS library uses). No API key, no quota, needs internet. */
object GTts {
    @Volatile var lastError: String = ""

    suspend fun tts(text: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val t = text.trim().take(190)
            if (t.isEmpty()) return@withContext null
            val lang = if (t.any { it in '\u0900'..'\u097F' }) "hi" else "en"
            val url = "https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=$lang&q=" + URLEncoder.encode(t, "UTF-8")
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 8000
                c.readTimeout = 8000
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                if (c.responseCode !in 200..299) {
                    lastError = "HTTP " + c.responseCode
                    return@withContext null
                }
                val bytes = c.inputStream.use { it.readBytes() }
                if (bytes.size < 200) {
                    lastError = "empty audio"
                    return@withContext null
                }
                lastError = ""
                bytes
            } finally {
                c.disconnect()
            }
        } catch (e: Exception) {
            lastError = e.javaClass.simpleName
            null
        }
    }
}
