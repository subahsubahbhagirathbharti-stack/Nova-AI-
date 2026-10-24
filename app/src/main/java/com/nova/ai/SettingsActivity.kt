package com.nova.ai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private val permsLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { }


    private fun watch(sb: SeekBar, f: () -> Unit) {
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) = f()
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_settings)
        val p = Prefs(this)
        val key = findViewById<EditText>(R.id.apiKey).apply { setText(p.apiKey) }
        val model = findViewById<EditText>(R.id.model).apply { setText(p.model) }
        val temp = findViewById<SeekBar>(R.id.temp).apply { progress = (p.temperature * 10).toInt() }
        val tempLabel = findViewById<TextView>(R.id.tempLabel)
        val max = findViewById<EditText>(R.id.maxTokens).apply { setText(p.maxTokens.toString()) }
        val user = findViewById<EditText>(R.id.userName).apply { setText(p.userName) }
        val city = findViewById<EditText>(R.id.city).apply { setText(p.city) }
        val lang = findViewById<EditText>(R.id.listenLang).apply { setText(p.listenLang) }
        val briefT = findViewById<EditText>(R.id.briefTime).apply { setText(p.briefTime) }
        val tts = findViewById<SwitchCompat>(R.id.ttsSwitch).apply { isChecked = p.speak }
        val eKey = findViewById<EditText>(R.id.elevenKey).apply { setText(p.elevenKey) }
        val eVoice = findViewById<EditText>(R.id.elevenVoiceId).apply { setText(p.elevenVoiceId) }
        val eModel = findViewById<EditText>(R.id.elevenModel).apply { setText(p.elevenModel) }
        val wakeSw = findViewById<SwitchCompat>(R.id.wakeSwitch).apply { isChecked = p.wakeMode }
        val turboSw = findViewById<SwitchCompat>(R.id.turboSwitch).apply { isChecked = p.turbo }
        val fastSw = findViewById<SwitchCompat>(R.id.fastSwitch).apply { isChecked = p.fastVoice }
        val liteSw = findViewById<SwitchCompat>(R.id.liteSwitch).apply { isChecked = p.liteUi }
        val autoSw = findViewById<SwitchCompat>(R.id.autoSwitch).apply { isChecked = p.autoStart }
        val musicSw = findViewById<SwitchCompat>(R.id.musicSwitch).apply { isChecked = p.musicQuiet }
        val gttsSw = findViewById<SwitchCompat>(R.id.gttsSwitch).apply { isChecked = p.gtts }
        val angerSw = findViewById<SwitchCompat>(R.id.angerSwitch).apply { isChecked = p.angerLock }
        val nat = findViewById<SwitchCompat>(R.id.natSwitch).apply { isChecked = p.naturalVoice }
        val vname = findViewById<EditText>(R.id.voiceName).apply { setText(p.voiceName) }
        val tmodel = findViewById<EditText>(R.id.ttsModel).apply { setText(p.ttsModel) }
        val rate = findViewById<SeekBar>(R.id.rate).apply { progress = (p.rate * 10).toInt() - 5 }
        val pitch = findViewById<SeekBar>(R.id.pitch).apply { progress = (p.pitch * 10).toInt() - 5 }
        val rateLabel = findViewById<TextView>(R.id.rateLabel)
        val pitchLabel = findViewById<TextView>(R.id.pitchLabel)

        fun labels() {
            tempLabel.text = "Temperature: %.1f (0.7 to 1.0 works best)".format(temp.progress / 10f)
            rateLabel.text = "Speed: %.1f (phone voice only)".format((rate.progress + 5) / 10f)
            pitchLabel.text = "Pitch: %.1f (phone voice only)".format((pitch.progress + 5) / 10f)
        }
        labels()
        watch(temp) { labels() }
        watch(rate) { labels() }
        watch(pitch) { labels() }

        findViewById<Button>(R.id.btnA11y).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        fun saveCore() {
            p.apiKey = key.text.toString().trim()
            p.model = model.text.toString().trim().ifEmpty { "gemini-3.8-flash" }
            p.voiceName = vname.text.toString().trim().ifEmpty { "Kore" }
            p.ttsModel = tmodel.text.toString().trim().ifEmpty { "gemini-3.8-flash-tts" }
        }
        fun report(title: String, msg: String) {
            android.app.AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show()
        }
        findViewById<Button>(R.id.btnTestChat).setOnClickListener {
            saveCore()
            Toast.makeText(this, "Test चल रहा है…", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                report("Gemini chat test", GeminiClient.ping(p))
            }
        }
        findViewById<Button>(R.id.btnTestVoice).setOnClickListener {
            saveCore()
            Toast.makeText(this, "Voice test चल रहा है…", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                val started = System.currentTimeMillis()
                val pcm = GeminiClient.tts("नमस्ते, मैं नोवा हूँ।", p)
                val secs = (System.currentTimeMillis() - started) / 1000.0
                if (pcm != null && pcm.size > 2) {
                    Speaker.netBlockedUntil = 0L
                    report("Gemini voice test", "OK — natural आवाज़ बन गई (%.1f सेकंड)। अब सुनाती हूँ।".format(secs))
                    Speaker(this@SettingsActivity, p).speak("नमस्ते, मैं नोवा हूँ।", lifecycleScope, true, natural = true)
                } else {
                    report("Gemini voice test", "आवाज़ नहीं बनी।\n\nकारण: " + GeminiClient.lastTtsError.ifBlank { "पता नहीं चला" } +
                        "\n\nइसका screenshot Claude को भेज दो।")
                }
            }
        }
        findViewById<Button>(R.id.btnPickVoice).setOnClickListener {
            var engine: android.speech.tts.TextToSpeech? = null
            engine = android.speech.tts.TextToSpeech(this) { st ->
                val e = engine
                if (st != android.speech.tts.TextToSpeech.SUCCESS || e == null) {
                    report("Phone voice", "इस फोन में Text-to-speech engine तैयार नहीं है।")
                    e?.shutdown()
                } else {
                    val voices = (e.voices ?: emptySet())
                        .sortedWith(compareBy({ it.locale.language != "hi" }, { it.locale.language }, { it.name }))
                    if (voices.isEmpty()) {
                        report("Phone voice", "कोई voice नहीं मिली। नीचे वाले बटन से और आवाज़ें install करो।")
                        e.shutdown()
                    } else {
                        val labels = voices.map { v ->
                            v.locale.displayName + " · " + v.name + (if (v.isNetworkConnectionRequired) " (online)" else "")
                        }.toTypedArray()
                        var chosen = -1
                        android.app.AlertDialog.Builder(this).setTitle("आवाज़ चुनो: दबाकर सुनो, फिर Save")
                            .setSingleChoiceItems(labels, -1) { _, idx ->
                                chosen = idx
                                val v = voices[idx]
                                e.voice = v
                                val sample = when (v.locale.language) {
                                    "hi" -> "नमस्ते, मैं नोवा हूँ। तुम कैसे हो?"
                                    "en" -> "Hello, I am Nova. How are you?"
                                    else -> "Nova"
                                }
                                e.speak(sample, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "preview")
                            }
                            .setPositiveButton("Save") { _, _ ->
                                if (chosen >= 0) {
                                    p.sysVoice = voices[chosen].name
                                    Toast.makeText(this, "चुनी: " + voices[chosen].name, Toast.LENGTH_LONG).show()
                                }
                            }
                            .setNeutralButton("Default") { _, _ -> p.sysVoice = "" }
                            .setNegativeButton("Close", null)
                            .setOnDismissListener { e.shutdown() }
                            .show()
                    }
                }
            }
        }
        findViewById<Button>(R.id.btnPickGemini).setOnClickListener {
            val names = arrayOf("Zephyr", "Puck", "Charon", "Kore", "Fenrir", "Leda", "Orus", "Aoede", "Callirrhoe", "Autonoe",
                "Enceladus", "Iapetus", "Umbriel", "Algieba", "Despina", "Erinome", "Algenib", "Rasalgethi", "Laomedeia",
                "Achernar", "Alnilam", "Schedar", "Gacrux", "Pulcherrima", "Achird", "Zubenelgenubi", "Vindemiatrix",
                "Sadachbia", "Sadaltager", "Sulafat")
            var chosen = -1
            android.app.AlertDialog.Builder(this).setTitle("Gemini आवाज़ चुनो")
                .setSingleChoiceItems(names, -1) { _, idx -> chosen = idx }
                .setPositiveButton("Save") { _, _ ->
                    if (chosen >= 0) {
                        vname.setText(names[chosen])
                        p.voiceName = names[chosen]
                        Toast.makeText(this, names[chosen] + " चुनी। सुनने के लिए Test Gemini voice दबाओ।", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Close", null)
                .show()
        }
        findViewById<Button>(R.id.btnPickLang).setOnClickListener {
            val codes = arrayOf("hi-IN", "en-IN", "bn-IN", "ta-IN", "te-IN", "mr-IN", "gu-IN", "kn-IN", "ml-IN", "pa-IN", "ur-IN", "en-US")
            val labels = arrayOf("हिंदी", "English (India)", "বাংলা", "தமிழ்", "తెలుగు", "मराठी", "ગુજરાતી", "ಕನ್ನಡ", "മലയാളം", "ਪੰਜਾਬੀ", "اردو", "English (US)")
            android.app.AlertDialog.Builder(this).setTitle("सुनने की भाषा")
                .setItems(labels) { _, i ->
                    lang.setText(codes[i])
                    p.listenLang = codes[i]
                    Toast.makeText(this, "भाषा: " + labels[i], Toast.LENGTH_SHORT).show()
                }
                .show()
        }
        findViewById<Button>(R.id.btnTtsSettings).setOnClickListener {
            try {
                startActivity(Intent("com.android.settings.TTS_SETTINGS"))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
        fun openScreen(i: Intent) {
            try { startActivity(i) } catch (e: Exception) { Toast.makeText(this, "ये screen इस फोन पर नहीं खुली", Toast.LENGTH_LONG).show() }
        }
        findViewById<Button>(R.id.btnPermAll).setOnClickListener {
            val want = ArrayList<String>()
            want.add(android.Manifest.permission.RECORD_AUDIO)
            want.add(android.Manifest.permission.READ_CONTACTS)
            want.add(android.Manifest.permission.CALL_PHONE)
            if (android.os.Build.VERSION.SDK_INT >= 33) want.add(android.Manifest.permission.POST_NOTIFICATIONS)
            val need = want.filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
            if (need.isEmpty()) Toast.makeText(this, "ये सारी permissions पहले से मिली हुई हैं ✅", Toast.LENGTH_LONG).show()
            else permsLauncher.launch(need.toTypedArray())
        }
        findViewById<Button>(R.id.btnPermNotif).setOnClickListener { openScreen(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        findViewById<Button>(R.id.btnPermBattery).setOnClickListener {
            openScreen(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.btnPermOverlay).setOnClickListener {
            openScreen(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.btnPermAutostart).setOnClickListener {
            try {
                startActivity(Intent().setComponent(android.content.ComponentName("com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity")))
            } catch (e: Exception) {
                openScreen(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }
        findViewById<Button>(R.id.btnPermXiaomiBattery).setOnClickListener {
            try {
                startActivity(Intent().setComponent(android.content.ComponentName("com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
                    .putExtra("package_name", packageName).putExtra("package_label", "Nova AI"))
            } catch (e: Exception) {
                openScreen(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }
        }
        findViewById<Button>(R.id.btnPermApp).setOnClickListener {
            openScreen(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            ChatDb(this).clear(); Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            p.apiKey = key.text.toString().trim()
            p.model = model.text.toString().trim().ifEmpty { "gemini-3.8-flash" }
            p.temperature = temp.progress / 10f
            p.maxTokens = max.text.toString().toIntOrNull()?.coerceIn(64, 8192) ?: 2048
            p.userName = user.text.toString().trim().ifEmpty { "love" }
            p.city = city.text.toString().trim()
            p.listenLang = lang.text.toString().trim().ifEmpty { "hi-IN" }
            p.briefTime = briefT.text.toString().trim()
            Brief.schedule(this, p)
            p.speak = tts.isChecked
            p.elevenKey = eKey.text.toString().trim()
            p.elevenVoiceId = eVoice.text.toString().trim()
            p.elevenModel = eModel.text.toString().trim().ifEmpty { "eleven_multilingual_v2" }
            p.wakeMode = wakeSw.isChecked
            p.turbo = turboSw.isChecked
            p.fastVoice = fastSw.isChecked
            p.liteUi = liteSw.isChecked
            p.autoStart = autoSw.isChecked
            p.musicQuiet = musicSw.isChecked
            p.gtts = gttsSw.isChecked
            p.angerLock = angerSw.isChecked
            p.naturalVoice = nat.isChecked
            p.voiceName = vname.text.toString().trim().ifEmpty { "Kore" }
            p.ttsModel = tmodel.text.toString().trim().ifEmpty { "gemini-3.8-flash-tts" }
            p.rate = (rate.progress + 5) / 10f
            p.pitch = (pitch.progress + 5) / 10f
            Toast.makeText(this, "Saved 💙", Toast.LENGTH_SHORT).show(); finish()
        }
    }
}
