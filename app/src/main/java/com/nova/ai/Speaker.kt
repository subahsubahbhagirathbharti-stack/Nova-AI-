package com.nova.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/** Voice priority: ElevenLabs -> Gemini natural voice -> phone's built-in voice. */
class Speaker(private val ctx: Context, private val prefs: Prefs) {
    private var ready = false
    private val handler = Handler(Looper.getMainLooper())
    private var pendingFinish: (() -> Unit)? = null
    private val tts = TextToSpeech(ctx) { ready = (it == TextToSpeech.SUCCESS) }
    private var track: AudioTrack? = null
    private var player: MediaPlayer? = null
    private var gen = 0

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { handler.post { pendingFinish?.invoke() } }
            override fun onError(id: String?) { handler.post { pendingFinish?.invoke() } }
        })
    }

    fun speak(raw: String, scope: CoroutineScope, force: Boolean = false, onDone: () -> Unit = {}) {
        if (!force && !prefs.speak) { onDone(); return }
        val text = clean(raw)
        if (text.isBlank()) { onDone(); return }
        stop()
        val my = gen
        val finish: () -> Unit = { if (my == gen) onDone() }
        scope.launch {
            if (prefs.elevenKey.isNotBlank() && prefs.elevenVoiceId.isNotBlank()) {
                val b = ElevenLabs.tts(text, prefs)
                if (my != gen) return@launch
                if (b != null && playMp3(b, finish)) return@launch
            }
            if (prefs.naturalVoice && prefs.apiKey.isNotBlank()) {
                val pcm = GeminiClient.tts(text, prefs)
                if (my != gen) return@launch
                if (pcm != null && pcm.size > 2) { play(pcm, finish); return@launch }
            }
            system(text, finish)
        }
    }

    private fun system(text: String, finish: () -> Unit) {
        if (!ready) { finish(); return }
        val hindi = text.any { it in '\u0900'..'\u097F' }
        tts.setLanguage(if (hindi) Locale("hi", "IN") else Locale.getDefault())
        tts.setPitch(prefs.pitch)
        tts.setSpeechRate(prefs.rate)
        pendingFinish = finish
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nova")
    }

    private fun playMp3(bytes: ByteArray, finish: () -> Unit): Boolean = try {
        val f = File(ctx.cacheDir, "nova_tts.mp3")
        f.writeBytes(bytes)
        val mp = MediaPlayer()
        mp.setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        mp.setDataSource(f.absolutePath)
        mp.setOnCompletionListener { finish() }
        mp.setOnErrorListener { _, _, _ -> finish(); true }
        mp.prepare()
        mp.start()
        player = mp
        true
    } catch (e: Exception) {
        false
    }

    private fun play(pcm: ByteArray, finish: () -> Unit) {
        val n = pcm.size - (pcm.size % 2)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(24000)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(n)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(pcm, 0, n)
        track = t
        t.play()
        handler.postDelayed({ finish() }, n * 1000L / 48000L + 250L)
    }

    fun stop() {
        gen++
        handler.removeCallbacksAndMessages(null)
        pendingFinish = null
        tts.stop()
        track?.let {
            try { it.stop() } catch (e: Exception) { }
            it.release()
        }
        track = null
        player?.let {
            try { it.stop() } catch (e: Exception) { }
            it.release()
        }
        player = null
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }

    private fun clean(s: String): String =
        s.replace(Regex("```[\\s\\S]*?```"), " code block ")
            .replace(Regex("[*_#`>~]"), "")
            .replace(Regex("[\\p{So}\\p{Cs}\\uFE0F\\u200D]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(500)
}
