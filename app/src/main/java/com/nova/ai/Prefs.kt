package com.nova.ai

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("nova", Context.MODE_PRIVATE)
    var apiKey: String get() = sp.getString("key", "") ?: ""; set(v) = sp.edit().putString("key", v).apply()
    var model: String get() = sp.getString("model", "gemini-2.5-flash") ?: "gemini-2.5-flash"; set(v) = sp.edit().putString("model", v).apply()
    var temperature: Float get() = sp.getFloat("temp", 0.8f); set(v) = sp.edit().putFloat("temp", v).apply()
    var maxTokens: Int get() = sp.getInt("max", 2048); set(v) = sp.edit().putInt("max", v).apply()
    var userName: String get() = sp.getString("user", "love") ?: "love"; set(v) = sp.edit().putString("user", v).apply()
    var speak: Boolean get() = sp.getBoolean("tts", false); set(v) = sp.edit().putBoolean("tts", v).apply()
}
