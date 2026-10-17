package com.nova.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class W(val city: String, val temp: Double, val code: Int, val hum: Int, val wind: Double)

/** Free weather from Open-Meteo (no API key). */
object Weather {
    private var cachedKey = ""
    private var cachedAt = 0L
    private var cached: W? = null

    fun isAsk(t: String): Boolean = t.contains("weather") || t.contains("मौसम") || t.contains("mausam")

    private fun get(u: String): String? = try {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 10000
        try {
            if (c.responseCode in 200..299) c.inputStream.bufferedReader().readText() else null
        } finally {
            c.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    private fun cond(code: Int): Pair<String, String> = when (code) {
        0 -> "Clear" to "आसमान साफ़ है"
        1, 2 -> "Partly cloudy" to "थोड़े बादल हैं"
        3 -> "Cloudy" to "बादल छाए हैं"
        45, 48 -> "Fog" to "कोहरा है"
        in 51..57 -> "Drizzle" to "बूंदाबांदी हो रही है"
        in 61..67 -> "Rain" to "बारिश हो रही है"
        in 71..77 -> "Snow" to "बर्फ़ गिर रही है"
        in 80..82 -> "Showers" to "बौछारें पड़ रही हैं"
        in 95..99 -> "Thunderstorm" to "आंधी-तूफ़ान है"
        else -> "—" to "मौसम बदल रहा है"
    }

    suspend fun fetch(city: String): W? = withContext(Dispatchers.IO) {
        try {
            val key = city.trim().lowercase()
            if (key.isEmpty()) return@withContext null
            if (key == cachedKey && cached != null && System.currentTimeMillis() - cachedAt < 15 * 60 * 1000L) return@withContext cached
            val g = get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + URLEncoder.encode(city.trim(), "UTF-8"))
                ?: return@withContext null
            val results = JSONObject(g).optJSONArray("results") ?: return@withContext null
            val loc = results.getJSONObject(0)
            val w = get("https://api.open-meteo.com/v1/forecast?latitude=" + loc.getDouble("latitude") +
                "&longitude=" + loc.getDouble("longitude") +
                "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m")
                ?: return@withContext null
            val cur = JSONObject(w).getJSONObject("current")
            val res = W(loc.optString("name", city), cur.getDouble("temperature_2m"), cur.getInt("weather_code"),
                cur.getInt("relative_humidity_2m"), cur.getDouble("wind_speed_10m"))
            cachedKey = key
            cachedAt = System.currentTimeMillis()
            cached = res
            res
        } catch (e: Exception) {
            null
        }
    }

    suspend fun hud(city: String): String {
        if (city.isBlank()) return "Set city in ⚙"
        val w = fetch(city) ?: return "Weather offline"
        return "%.0f°C · %s".format(w.temp, cond(w.code).first)
    }

    suspend fun summaryHi(city: String): String {
        if (city.isBlank()) return "पहले Settings में अपना शहर डाल दो, फिर मैं मौसम बताऊँगी।"
        val w = fetch(city) ?: return "अभी मौसम नहीं मिल पाया, नेट चेक करो।"
        return "%s में अभी %.0f°C है, %s। नमी %d%% और हवा %.0f km/h की है।".format(w.city, w.temp, cond(w.code).second, w.hum, w.wind)
    }
}
