package com.nova.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Hands-free mode: keeps listening, acts, answers aloud, and listens again until told to stop. */
class ListenService : Service() {
    companion object {
        const val ACTION_STOP = "com.nova.ai.STOP"
        @Volatile var running = false
        var lastState = "idle"
        var onState: ((String) -> Unit)? = null
        var onChange: (() -> Unit)? = null
    }

    private lateinit var prefs: Prefs
    private lateinit var db: ChatDb
    private lateinit var speaker: Speaker
    private var recognizer: SpeechRecognizer? = null
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wake: PowerManager.WakeLock? = null
    private var busy = false
    private var awakeUntil = 0L
    private val wakeWords = listOf("nova", "नोवा", "नॉवा", "नोवाह", "novah")

    /** True for "Nova", "Hello Nova", "हेलो नोवा", "hey nova" - just calling her, no command. */
    private fun isBareWake(s: String): Boolean {
        var rest = s.lowercase()
        if (wakeWords.none { rest.contains(it) }) return false
        for (w in wakeWords) rest = rest.replace(w, " ")
        rest = rest.replace(Regex("(hello|hey|hi|okay|ok|हेलो|हैलो|हेल्लो|हे|हाय|ओके|सुनो|सुनिए)"), " ")
            .replace(Regex("[\\s,.!?।-]+"), "")
        return rest.isEmpty()
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (running) return START_NOT_STICKY
        startForegroundCompat()
        running = true
        prefs = Prefs(this)
        db = ChatDb(this)
        speaker = Speaker(this, prefs)
        speaker.onInfo = { m -> android.widget.Toast.makeText(applicationContext, m, android.widget.Toast.LENGTH_LONG).show() }
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nova:listen").apply { acquire(2 * 60 * 60 * 1000L) }
        busy = true
        state("speaking")
        speaker.speak("हाँ ${prefs.userName}, मैं सुन रही हूँ।", scope, true) { busy = false; awakeUntil = System.currentTimeMillis() + 20000L; listen() }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val chId = "nova_listen"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(chId, "Nova listening", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0,
            Intent(this, ListenService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, chId)
            .setContentTitle("Nova सुन रही है")
            .setContentText("जगाने के लिए बोलो \"नोवा\" · रोकने के लिए \"नोवा बंद\" या Stop")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, n)
    }

    private fun state(s: String) { lastState = s; onState?.invoke(s) }
    private fun changed() { onChange?.invoke() }

    private fun listen() {
        if (!running) return
        recognizer?.destroy()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "इस फोन में speech recognition नहीं है", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(b: Bundle?) {
                // Instant wake: as soon as the partial text is just "Nova", answer without waiting for silence.
                if (busy || !prefs.wakeMode || System.currentTimeMillis() <= awakeUntil) return
                val pt = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                if (isBareWake(pt)) {
                    recognizer?.destroy()
                    recognizer = null
                    handle(pt)
                }
            }
            override fun onEvent(t: Int, b: Bundle?) {}
            override fun onError(e: Int) {
                if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { stopSelf(); return }
                relisten(if (e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1200L else 300L)
            }
            override fun onResults(b: Bundle?) {
                if (busy) return
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (t.isNullOrBlank()) relisten(200L) else handle(t)
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, prefs.listenLang)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
        r.startListening(i)
        state("listening")
    }

    private fun relisten(delay: Long) {
        main.postDelayed({ if (running && !busy) listen() }, delay)
    }

    private fun stopScrollPhrase(t: String) =
        (t.contains("scroll") || t.contains("स्क्रॉल")) && (t.contains("stop") || t.contains("बंद") || t.contains("रुक") || t.contains("रोक"))

    private fun stopListenPhrase(t: String) =
        listOf("stop listening", "listening band", "सुनना बंद", "बाय नोवा", "bye nova", "नोवा बंद", "nova band", "बंद हो जाओ")
            .any { t.contains(it) }

    private fun stopWord(t: String) = t in setOf("stop", "ruko", "रुको", "रुक जाओ", "ruk jao", "बस", "रुक")

    private fun handle(raw: String) {
        busy = true
        val tRaw = raw.trim().lowercase()
        var text = raw.trim()
        if (prefs.wakeMode) {
            val now = System.currentTimeMillis()
            val hit = wakeWords.firstOrNull { tRaw.contains(it) }
            if (hit == null && now > awakeUntil) {
                // Nobody called Nova: ignore and keep waiting quietly.
                busy = false
                relisten(100L)
                return
            }
            if (hit != null) {
                text = text.replace(Regex(Regex.escape(hit), RegexOption.IGNORE_CASE), " ")
                    .replace(Regex("^[\\s,.!?।-]+|[\\s,.!?।-]+$"), "")
                    .trim()
                if ((text.isBlank() || isBareWake(raw)) && !stopListenPhrase(tRaw)) {
                    awakeUntil = now + 20000L
                    state("thinking")
                    speaker.speak("हाँ, बोलो।", scope, true, onStart = { state("speaking") }) {
                        busy = false
                        awakeUntil = System.currentTimeMillis() + 20000L
                        relisten(100L)
                    }
                    return
                }
                if (text.isBlank()) text = raw.trim()
            }
        }
        val t = text.trim().lowercase()
        db.add(Msg("user", text))
        changed()
        when {
            stopScrollPhrase(t) -> {
                NovaAccessibilityService.instance?.stopAuto()
                finishTurn("", speak = false)
            }
            stopListenPhrase(tRaw) -> finishTurn("ठीक है, मैं बंद हो रही हूँ। जब बुलाना हो, बटन दबा देना।", stopAfter = true)
            stopWord(t) -> {
                NovaAccessibilityService.instance?.stopAuto()
                speaker.stop()
                busy = false
                relisten(100L)
            }
            else -> scope.launch {
                state("thinking")
                try {
                    val norm = CommandRouter.normalize(text)
                    val local = CommandRouter.handle(this@ListenService, text, prefs.userName)
                    val answer: String = when {
                        local != null -> local
                        Brief.isAsk(t) -> Brief.build(applicationContext, prefs)
                        Weather.isAsk(t) -> Weather.summaryHi(prefs.city)
                        else -> GeminiClient.chat(applicationContext, db.all(), prefs, false)
                    }
                    finishTurn(answer, speak = !CommandRouter.isScreenCommand(norm))
                } catch (e: Exception) {
                    finishTurn("अभी ये नहीं हो पाया।")
                }
            }
        }
    }

    private fun finishTurn(answer: String, speak: Boolean = true, stopAfter: Boolean = false) {
        if (answer.isNotBlank()) { db.add(Msg("model", answer)); changed() }
        val spoken = when {
            !speak -> ""
            answer.startsWith("Network problem") || answer.startsWith("Gemini error") || answer.startsWith("(No reply") ->
                "अभी कुछ गड़बड़ हो गई, फिर से बोलो।"
            else -> answer
        }
        speaker.speak(spoken, scope, true, onStart = { state("speaking") }) {
            busy = false
            awakeUntil = System.currentTimeMillis() + 20000L
            if (stopAfter) stopSelf() else relisten(100L)
        }
    }

    override fun onDestroy() {
        running = false
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        if (::speaker.isInitialized) speaker.shutdown()
        wake?.let { if (it.isHeld) it.release() }
        scope.cancel()
        state("idle")
        changed()
        super.onDestroy()
    }
}
