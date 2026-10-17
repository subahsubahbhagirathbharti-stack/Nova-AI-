package com.nova.ai

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : AppCompatActivity() {

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
        val tts = findViewById<SwitchCompat>(R.id.ttsSwitch).apply { isChecked = p.speak }
        val eKey = findViewById<EditText>(R.id.elevenKey).apply { setText(p.elevenKey) }
        val eVoice = findViewById<EditText>(R.id.elevenVoiceId).apply { setText(p.elevenVoiceId) }
        val eModel = findViewById<EditText>(R.id.elevenModel).apply { setText(p.elevenModel) }
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
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            ChatDb(this).clear(); Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            p.apiKey = key.text.toString().trim()
            p.model = model.text.toString().trim().ifEmpty { "gemini-2.5-flash" }
            p.temperature = temp.progress / 10f
            p.maxTokens = max.text.toString().toIntOrNull()?.coerceIn(64, 8192) ?: 2048
            p.userName = user.text.toString().trim().ifEmpty { "love" }
            p.city = city.text.toString().trim()
            p.listenLang = lang.text.toString().trim().ifEmpty { "hi-IN" }
            p.speak = tts.isChecked
            p.elevenKey = eKey.text.toString().trim()
            p.elevenVoiceId = eVoice.text.toString().trim()
            p.elevenModel = eModel.text.toString().trim().ifEmpty { "eleven_multilingual_v2" }
            p.naturalVoice = nat.isChecked
            p.voiceName = vname.text.toString().trim().ifEmpty { "Kore" }
            p.ttsModel = tmodel.text.toString().trim().ifEmpty { "gemini-3.1-flash-tts-preview" }
            p.rate = (rate.progress + 5) / 10f
            p.pitch = (pitch.progress + 5) / 10f
            Toast.makeText(this, "Saved 💙", Toast.LENGTH_SHORT).show(); finish()
        }
    }
}
