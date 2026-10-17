package com.nova.ai

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var db: ChatDb
    private lateinit var prefs: Prefs
    private lateinit var speaker: Speaker
    private lateinit var adapter: ChatAdapter
    private lateinit var list: RecyclerView
    private lateinit var codeSwitch: SwitchCompat
    private lateinit var reactor: ArcReactorView
    private lateinit var status: TextView
    private lateinit var mic: Button
    private lateinit var weather: TextView
    private lateinit var battery: TextView
    private val msgs = mutableListOf<Msg>()

    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.RECORD_AUDIO] == true) startListening()
        else Toast.makeText(this, "Mic की permission देनी होगी", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)
        db = ChatDb(this); prefs = Prefs(this)
        speaker = Speaker(this, prefs)
        msgs.addAll(db.all())
        list = findViewById(R.id.chatList)
        list.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        adapter = ChatAdapter(msgs); list.adapter = adapter
        codeSwitch = findViewById(R.id.codeSwitch)
        reactor = findViewById(R.id.reactor)
        status = findViewById(R.id.status)
        mic = findViewById(R.id.btnMic)
        weather = findViewById(R.id.weather)
        battery = findViewById(R.id.battery)
        val input = findViewById<EditText>(R.id.input)

        findViewById<Button>(R.id.btnSend).setOnClickListener {
            val t = input.text.toString().trim()
            if (t.isNotEmpty()) { input.text.clear(); submit(t) }
        }
        mic.setOnClickListener { speaker.stop(); toggleListening() }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        if (msgs.isEmpty()) addMsg(Msg("model", "Hey ${prefs.userName} 💙 मैं Nova हूँ। नीचे 🎙 दबाओ, फिर मैं लगातार सुनती रहूँगी, जब तक तुम \"नोवा बंद\" न बोलो। Gemini key ⚙ Settings में डालना मत भूलना।"))
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }

    private fun handleIntent(i: Intent) {
        if (i.getBooleanExtra("code", false)) codeSwitch.isChecked = true
        if (i.getBooleanExtra("voice", false) && !ListenService.running) toggleListening()
    }

    private fun startListening() {
        ContextCompat.startForegroundService(this, Intent(this, ListenService::class.java))
    }

    private fun toggleListening() {
        if (ListenService.running) {
            startService(Intent(this, ListenService::class.java).setAction(ListenService.ACTION_STOP))
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            val p = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
            perms.launch(p.toTypedArray())
        }
    }

    private fun applyState(s: String) {
        reactor.mode = s
        status.text = when (s) {
            "listening" -> "LISTENING…"
            "thinking" -> "PROCESSING…"
            "speaking" -> "SPEAKING…"
            else -> "STANDBY"
        }
        mic.text = if (ListenService.running) "⏹" else "🎙"
    }

    private fun addMsg(m: Msg) {
        db.add(m); msgs.add(m)
        adapter.notifyItemInserted(msgs.size - 1)
        list.scrollToPosition(msgs.size - 1)
    }

    private fun reload() {
        val fresh = db.all()
        msgs.clear(); msgs.addAll(fresh)
        adapter.notifyDataSetChanged()
        if (msgs.isNotEmpty()) list.scrollToPosition(msgs.size - 1)
    }

    private fun submit(text: String) {
        speaker.stop()
        addMsg(Msg("user", text))
        val local = CommandRouter.handle(this, text, prefs.userName)
        if (local != null) { reply(local); return }
        if (Weather.isAsk(text.lowercase())) {
            lifecycleScope.launch { reply(Weather.summaryHi(prefs.city)) }
            return
        }
        applyState("thinking")
        lifecycleScope.launch {
            val r = GeminiClient.chat(applicationContext, msgs, prefs, codeSwitch.isChecked)
            applyState(if (ListenService.running) ListenService.lastState else "idle")
            reply(r)
        }
    }

    private fun reply(text: String) {
        addMsg(Msg("model", text))
        speaker.speak(text, lifecycleScope)
    }

    private fun updateBattery() {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val charging = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
        battery.text = "BAT ${lvl * 100 / scale}%" + if (charging) " ⚡" else ""
    }

    override fun onResume() {
        super.onResume()
        reload()
        ListenService.onState = { applyState(it) }
        ListenService.onChange = { reload() }
        applyState(if (ListenService.running) ListenService.lastState else "idle")
        updateBattery()
        lifecycleScope.launch { weather.text = Weather.hud(prefs.city) }
    }

    override fun onPause() {
        ListenService.onState = null
        ListenService.onChange = null
        speaker.stop()
        super.onPause()
    }

    override fun onDestroy() { speaker.shutdown(); super.onDestroy() }
}
