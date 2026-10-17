package com.nova.ai

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : AppCompatActivity() {
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
        val tts = findViewById<SwitchCompat>(R.id.ttsSwitch).apply { isChecked = p.speak }

        fun label() { tempLabel.text = "Temperature: %.1f".format(temp.progress / 10f) }
        label()
        temp.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, v: Int, u: Boolean) = label()
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

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
            p.speak = tts.isChecked
            Toast.makeText(this, "Saved 💙", Toast.LENGTH_SHORT).show(); finish()
        }
    }
}
