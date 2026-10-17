package com.nova.ai

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var db: ChatDb
    private lateinit var prefs: Prefs
    private lateinit var adapter: ChatAdapter
    private lateinit var list: RecyclerView
    private lateinit var codeSwitch: SwitchCompat
    private val msgs = mutableListOf<Msg>()
    private var tts: TextToSpeech? = null

    private val voice = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val t = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (t != null) submit(t)
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)
        db = ChatDb(this); prefs = Prefs(this)
        msgs.addAll(db.all())
        list = findViewById(R.id.chatList)
        list.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        adapter = ChatAdapter(msgs); list.adapter = adapter
        codeSwitch = findViewById(R.id.codeSwitch)
        val input = findViewById<EditText>(R.id.input)

        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault() }

        findViewById<Button>(R.id.btnSend).setOnClickListener {
            val t = input.text.toString().trim()
            if (t.isNotEmpty()) { input.text.clear(); submit(t) }
        }
        findViewById<Button>(R.id.btnMic).setOnClickListener { listen() }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        if (msgs.isEmpty()) addMsg(Msg("model", "Hey ${prefs.userName} 💙 I'm Nova. Say \"open YouTube\", \"play lofi beats\", or just talk to me. Add your Gemini key in ⚙ Settings to unlock my brain."))
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }

    private fun handleIntent(i: Intent) {
        if (i.getBooleanExtra("code", false)) codeSwitch.isChecked = true
        if (i.getBooleanExtra("voice", false)) listen()
    }

    private fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "I'm listening…")
        try { voice.launch(i) } catch (e: Exception) {
            Toast.makeText(this, "No speech recognizer available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addMsg(m: Msg) {
        db.add(m); msgs.add(m)
        adapter.notifyItemInserted(msgs.size - 1)
        list.scrollToPosition(msgs.size - 1)
    }

    private fun submit(text: String) {
        addMsg(Msg("user", text))
        val local = CommandRouter.handle(this, text, prefs.userName)
        if (local != null) { reply(local); return }
        lifecycleScope.launch {
            val r = GeminiClient.chat(msgs, prefs, codeSwitch.isChecked)
            reply(r)
        }
    }

    private fun reply(text: String) {
        addMsg(Msg("model", text))
        if (prefs.speak) tts?.speak(text.replace(Regex("```[\\s\\S]*?```"), " code block "), TextToSpeech.QUEUE_FLUSH, null, "nova")
    }

    override fun onResume() {
        super.onResume()
        // refresh after "Clear history" in Settings
        val fresh = db.all()
        if (fresh.size != msgs.size) { msgs.clear(); msgs.addAll(fresh); adapter.notifyDataSetChanged() }
    }

    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }
}
