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
    private lateinit var reactor: AvatarView
    private lateinit var status: TextView
    private lateinit var mic: TextView
    private lateinit var homePage: android.view.View
    private lateinit var chatPage: android.view.View
    private lateinit var greeting: TextView
    private lateinit var greetName: TextView
    private lateinit var lastReply: TextView
    private lateinit var tapLabel: TextView
    private lateinit var weather: TextView
    private lateinit var battery: TextView
    private val msgs = mutableListOf<Msg>()

    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.RECORD_AUDIO] == true) startListening()
        else Toast.makeText(this, "Mic की permission देनी होगी", Toast.LENGTH_LONG).show()
    }

    private var camQuestion = ""
    private val attachments = ArrayList<ByteArray>()
    private var camToAttach = false
    private lateinit var attachBar: android.view.View
    private lateinit var attachThumb: android.widget.ImageView
    private lateinit var attachLabel: TextView

    private val pickImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) lifecycleScope.launch {
            val list = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { uris.take(4).mapNotNull { decodeUri(it) } }
            if (list.isEmpty()) Toast.makeText(this@MainActivity, "फोटो नहीं खुल पाई", Toast.LENGTH_SHORT).show()
            else setAttachments(list, "🖼 " + list.size + " फोटो")
        }
    }
    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "वीडियो से फ्रेम निकाल रही हूँ…", Toast.LENGTH_SHORT).show()
            val frames = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { videoFrames(uri) }
            if (frames.isEmpty()) Toast.makeText(this@MainActivity, "वीडियो नहीं खुल पाया", Toast.LENGTH_SHORT).show()
            else setAttachments(frames, "🎬 वीडियो (" + frames.size + " फ्रेम)")
        }
    }

    private fun decodeUri(u: android.net.Uri): ByteArray? {
        try {
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(u)?.use { android.graphics.BitmapFactory.decodeStream(it, null, o) }
            var sample = 1
            while (o.outWidth / sample > 1400 || o.outHeight / sample > 1400) sample *= 2
            val bmp = contentResolver.openInputStream(u)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
            val out = java.io.ByteArrayOutputStream()
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
            bmp.recycle()
            return out.toByteArray()
        } catch (e: Throwable) {
            return null
        }
    }

    /** Pulls 5 evenly spaced pictures out of a video (no audio), small enough for a low-RAM phone. */
    private fun videoFrames(u: android.net.Uri): List<ByteArray> {
        val r = android.media.MediaMetadataRetriever()
        val out = ArrayList<ByteArray>()
        try {
            r.setDataSource(this, u)
            val durMs = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val n = 5
            for (i in 0 until n) {
                val tMs = if (durMs > 0) durMs * (2 * i + 1) / (2 * n) else 0L
                val f = r.getFrameAtTime(tMs * 1000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
                val sc = minOf(1f, 768f / maxOf(f.width, f.height))
                val sm = if (sc < 1f) android.graphics.Bitmap.createScaledBitmap(f, (f.width * sc).toInt(), (f.height * sc).toInt(), true) else f
                val bos = java.io.ByteArrayOutputStream()
                sm.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, bos)
                out.add(bos.toByteArray())
                if (sm !== f) sm.recycle()
                f.recycle()
            }
        } catch (e: Throwable) {
        } finally {
            try { r.release() } catch (e: Throwable) { }
        }
        return out
    }

    private fun setAttachments(list: List<ByteArray>, label: String) {
        attachments.clear()
        attachments.addAll(list)
        val opt = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
        attachThumb.setImageBitmap(android.graphics.BitmapFactory.decodeByteArray(list[0], 0, list[0].size, opt))
        attachLabel.text = label + " — नीचे सवाल लिखो और SEND दबाओ"
        attachBar.visibility = android.view.View.VISIBLE
    }

    private fun clearAttachments() {
        attachments.clear()
        attachThumb.setImageDrawable(null)
        attachBar.visibility = android.view.View.GONE
    }

    private fun sendWithAttachments(q: String) {
        speaker.stop()
        val imgs = ArrayList(attachments)
        val label = attachLabel.text.toString().substringBefore(" —")
        val isVideo = label.startsWith("🎬")
        addMsg(Msg("user", "📎 " + label + "\n" + q))
        clearAttachments()
        applyState("thinking")
        lifecycleScope.launch {
            val question = if (isVideo) "ये एक वीडियो से बराबर दूरी पर लिए गए फ्रेम हैं (आवाज़ नहीं है)। " + q else q
            val r = GeminiClient.visionMulti(prefs, imgs, question)
            applyState(if (ListenService.running) ListenService.lastState else "idle")
            reply(r)
        }
    }

    private val askContacts = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private fun shotFile() = java.io.File(java.io.File(cacheDir, "images").apply { mkdirs() }, "nova_shot.jpg")
    private val takePic = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (!ok) {
            camToAttach = false
            Toast.makeText(this, "फोटो नहीं ली गई", Toast.LENGTH_SHORT).show()
        } else if (camToAttach) {
            camToAttach = false
            lifecycleScope.launch {
                val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { loadScaledJpeg(shotFile()) }
                if (bytes == null) Toast.makeText(this@MainActivity, "फोटो नहीं मिली", Toast.LENGTH_SHORT).show()
                else setAttachments(listOf(bytes), "📷 फोटो")
            }
        } else analyzePhoto()
    }

    private fun loadScaledJpeg(f: java.io.File): ByteArray? {
        if (!f.exists() || f.length() == 0L) return null
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(f.path, o)
        var sample = 1
        while (o.outWidth / sample > 1600 || o.outHeight / sample > 1600) sample *= 2
        val bmp = android.graphics.BitmapFactory.decodeFile(f.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
        bmp.recycle()
        return out.toByteArray()
    }

    private fun analyzePhoto() {
        addMsg(Msg("user", "📷 " + camQuestion))
        applyState("thinking")
        lifecycleScope.launch {
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { loadScaledJpeg(shotFile()) }
            val r = if (bytes == null) "फोटो नहीं मिली, फिर से कोशिश करो।" else GeminiClient.vision(prefs, bytes, camQuestion)
            applyState(if (ListenService.running) ListenService.lastState else "idle")
            reply(r)
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)
        db = ChatDb(this); prefs = Prefs(this)
        speaker = Speaker(this, prefs)
        speaker.onInfo = { m -> android.widget.Toast.makeText(applicationContext, m, android.widget.Toast.LENGTH_LONG).show() }
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
        homePage = findViewById(R.id.homePage)
        chatPage = findViewById(R.id.chatPage)
        greeting = findViewById(R.id.greeting)
        greetName = findViewById(R.id.greetName)
        lastReply = findViewById(R.id.lastReply)
        tapLabel = findViewById(R.id.tapLabel)
        attachBar = findViewById(R.id.attachBar)
        attachThumb = findViewById(R.id.attachThumb)
        attachLabel = findViewById(R.id.attachLabel)
        findViewById<TextView>(R.id.attachClear).setOnClickListener { clearAttachments() }
        findViewById<TextView>(R.id.btnPlus).setOnClickListener {
            val items = arrayOf("📷 Camera से फोटो", "🖼 Gallery से फोटो (4 तक)", "🎬 वीडियो चुनो")
            android.app.AlertDialog.Builder(this).setTitle("जोड़ो").setItems(items) { _, i ->
                when (i) {
                    0 -> {
                        camToAttach = true
                        try {
                            takePic.launch(androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", shotFile()))
                        } catch (e: Exception) {
                            camToAttach = false
                            Toast.makeText(this, "Camera नहीं खुल पाया", Toast.LENGTH_LONG).show()
                        }
                    }
                    1 -> pickImages.launch("image/*")
                    else -> pickVideo.launch("video/*")
                }
            }.show()
        }
        findViewById<TextView>(R.id.navHome).setOnClickListener { showPage(true) }
        findViewById<TextView>(R.id.navChat).setOnClickListener { showPage(false) }
        tapLabel.setOnClickListener { speaker.stop(); toggleListening() }
        reactor.setOnClickListener { speaker.stop(); toggleListening() }
        fun toChat(prefill: String) {
            showPage(false)
            input.setText(prefill)
            input.setSelection(input.text.length)
            input.requestFocus()
        }
        findViewById<TextView>(R.id.chipMusic).setOnClickListener { toChat("play ") }
        findViewById<TextView>(R.id.chipStudy).setOnClickListener { toChat("समझाओ: ") }
        findViewById<TextView>(R.id.chipCalc).setOnClickListener { toChat("calculate ") }
        findViewById<TextView>(R.id.chipNotes).setOnClickListener { submit("read notes") }
        findViewById<TextView>(R.id.chipWeather).setOnClickListener { submit("weather") }
        findViewById<TextView>(R.id.chipToday).setOnClickListener { submit("good morning") }

        findViewById<TextView>(R.id.btnSend).setOnClickListener {
            val t = input.text.toString().trim()
            if (attachments.isNotEmpty()) {
                input.text.clear()
                sendWithAttachments(if (t.isEmpty()) "इसमें क्या है? हिंदी में बताओ।" else t)
            } else if (t.isNotEmpty()) { input.text.clear(); submit(t) }
        }
        findViewById<TextView>(R.id.btnCam).setOnClickListener {
            camToAttach = false
            val q = input.text.toString().trim()
            camQuestion = if (q.isEmpty()) "इसमें क्या है? हिंदी में बताओ।" else q
            input.text.clear()
            try {
                val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", shotFile())
                takePic.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(this, "Camera नहीं खुल पाया", Toast.LENGTH_LONG).show()
            }
        }
        Brief.schedule(this, prefs)
        val flags = getSharedPreferences("flags", MODE_PRIVATE)
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED && !flags.getBoolean("askedContacts", false)) {
            flags.edit().putBoolean("askedContacts", true).apply()
            askContacts.launch(Manifest.permission.READ_CONTACTS)
        }
        mic.setOnClickListener { speaker.stop(); toggleListening() }
        findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        if (msgs.isEmpty()) addMsg(Msg("model", "Hey ${prefs.userName} 💙 मैं Nova हूँ। नीचे 🎙 दबाओ, फिर मैं लगातार सुनती रहूँगी, जब तक तुम \"नोवा बंद\" न बोलो। Gemini key ⚙ Settings में डालना मत भूलना।"))
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }

    private fun handleIntent(i: Intent) {
        if (i.getBooleanExtra("code", false)) codeSwitch.isChecked = true
        if (i.getBooleanExtra("brief", false)) { i.removeExtra("brief"); lifecycleScope.launch { reply(Brief.build(applicationContext, prefs)) } }
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
            p.add(Manifest.permission.READ_CONTACTS)
            perms.launch(p.toTypedArray())
        }
    }

    private fun showPage(home: Boolean) {
        homePage.visibility = if (home) android.view.View.VISIBLE else android.view.View.GONE
        chatPage.visibility = if (home) android.view.View.GONE else android.view.View.VISIBLE
        if (!home && msgs.isNotEmpty()) list.scrollToPosition(msgs.size - 1)
    }

    private fun updateGreeting() {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        greeting.text = when {
            h < 12 -> "Good morning,"
            h < 17 -> "Good afternoon,"
            else -> "Good evening,"
        }
        greetName.text = prefs.userName
    }

    private fun applyState(s: String) {
        reactor.mode = s
        status.text = when (s) {
            "listening" -> "● Listening…"
            "thinking" -> "● Thinking…"
            "speaking" -> "● Speaking…"
            else -> "● Ready when you are"
        }
        mic.text = if (ListenService.running) "STOP" else "MIC"
        tapLabel.text = if (ListenService.running) "Tap to stop" else "Tap to talk"
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
        if (Brief.isAsk(text.lowercase())) {
            lifecycleScope.launch { reply(Brief.build(applicationContext, prefs)) }
            return
        }
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
        lastReply.text = text.take(180)
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
        updateGreeting()
        reload()
        ListenService.onState = { applyState(it) }
        if (!ListenService.running) CommandRouter.followUp = { m -> reply(m) }
        ListenService.onChange = { reload() }
        applyState(if (ListenService.running) ListenService.lastState else "idle")
        updateBattery()
        lifecycleScope.launch { weather.text = Weather.hud(prefs.city) }
    }

    override fun onPause() {
        ListenService.onState = null
        ListenService.onChange = null
        super.onPause()
    }

    override fun onDestroy() { speaker.shutdown(); super.onDestroy() }
}
