package com.nova.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Free Google Translate voice (the same one the Python gTTS library uses). No API key, no quota, needs internet. */
object GTts {
    @Volatile var lastError: String = ""

    private fun langFor(t: String): String {
        for (ch in t) {
            when (ch) {
                in '\u0900'..'\u097F' -> return "hi"
                in '\u0980'..'\u09FF' -> return "bn"
                in '\u0A00'..'\u0A7F' -> return "pa"
                in '\u0A80'..'\u0AFF' -> return "gu"
                in '\u0B80'..'\u0BFF' -> return "ta"
                in '\u0C00'..'\u0C7F' -> return "te"
                in '\u0C80'..'\u0CFF' -> return "kn"
                in '\u0D00'..'\u0D7F' -> return "ml"
                in '\u0600'..'\u06FF' -> return "ur"
            }
        }
        return "en"
    }

    suspend fun tts(text: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val t = text.trim().take(190)
            if (t.isEmpty()) return@withContext null
            val lang = langFor(t)
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
