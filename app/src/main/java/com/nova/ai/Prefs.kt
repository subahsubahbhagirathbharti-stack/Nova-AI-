package com.nova.ai

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("nova", Context.MODE_PRIVATE)
    var apiKey: String get() = sp.getString("key", "") ?: ""; set(v) = sp.edit().putString("key", v).apply()
    var model: String get() = sp.getString("model", "gemini-3.8-flash") ?: "gemini-3.8-flash"; set(v) = sp.edit().putString("model", v).apply()
    var temperature: Float get() = sp.getFloat("temp", 0.8f); set(v) = sp.edit().putFloat("temp", v).apply()
    var maxTokens: Int get() = sp.getInt("max", 2048); set(v) = sp.edit().putInt("max", v).apply()
    var userName: String get() = sp.getString("user", "love") ?: "love"; set(v) = sp.edit().putString("user", v).apply()
    var speak: Boolean get() = sp.getBoolean("tts", false); set(v) = sp.edit().putBoolean("tts", v).apply()
    var briefTime: String get() = sp.getString("brieftime", "") ?: ""; set(v) = sp.edit().putString("brieftime", v).apply()
    var sysVoice: String get() = sp.getString("sysvoice", "") ?: ""; set(v) = sp.edit().putString("sysvoice", v).apply()
    var notes: String get() = sp.getString("notes", "") ?: ""; set(v) = sp.edit().putString("notes", v).apply()
    var gtts: Boolean get() = sp.getBoolean("gtts", true); set(v) = sp.edit().putBoolean("gtts", v).apply()
    var angerLock: Boolean get() = sp.getBoolean("angerlock", true); set(v) = sp.edit().putBoolean("angerlock", v).apply()
    var musicQuiet: Boolean get() = sp.getBoolean("musicquiet", true); set(v) = sp.edit().putBoolean("musicquiet", v).apply()
    var fastVoice: Boolean get() = sp.getBoolean("fastvoice", true); set(v) = sp.edit().putBoolean("fastvoice", v).apply()
    var liteUi: Boolean get() = sp.getBoolean("liteui", true); set(v) = sp.edit().putBoolean("liteui", v).apply()
    var autoStart: Boolean get() = sp.getBoolean("autostart", false); set(v) = sp.edit().putBoolean("autostart", v).apply()
    var turbo: Boolean get() = sp.getBoolean("turbo", true); set(v) = sp.edit().putBoolean("turbo", v).apply()
    var wakeMode: Boolean get() = sp.getBoolean("wakeword", true); set(v) = sp.edit().putBoolean("wakeword", v).apply()
    var naturalVoice: Boolean get() = sp.getBoolean("nat", true); set(v) = sp.edit().putBoolean("nat", v).apply()
    var voiceName: String get() = sp.getString("vname", "Kore") ?: "Kore"; set(v) = sp.edit().putString("vname", v).apply()
    var ttsModel: String get() = sp.getString("tmodel", "gemini-3.8-flash-tts") ?: "gemini-3.8-flash-tts"; set(v) = sp.edit().putString("tmodel", v).apply()
    var rate: Float get() = sp.getFloat("rate", 1.0f); set(v) = sp.edit().putFloat("rate", v).apply()
    var pitch: Float get() = sp.getFloat("pitch", 1.0f); set(v) = sp.edit().putFloat("pitch", v).apply()
    var memories: String get() = sp.getString("mem", "") ?: ""; set(v) = sp.edit().putString("mem", v).apply()

    var elevenKey: String get() = sp.getString("ekey", "") ?: ""; set(v) = sp.edit().putString("ekey", v).apply()
    var elevenVoiceId: String get() = sp.getString("evoice", "") ?: ""; set(v) = sp.edit().putString("evoice", v).apply()
    var elevenModel: String get() = sp.getString("emodel", "eleven_multilingual_v2") ?: "eleven_multilingual_v2"; set(v) = sp.edit().putString("emodel", v).apply()
    var city: String get() = sp.getString("city", "") ?: ""; set(v) = sp.edit().putString("city", v).apply()
    var listenLang: String get() = sp.getString("llang", "hi-IN") ?: "hi-IN"; set(v) = sp.edit().putString("llang", v).apply()

    fun addMemory(s: String) {
        memories = (memories + "\n" + s.trim()).trim().lines().takeLast(30).joinToString("\n")
    }
}
