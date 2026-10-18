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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Voice priority: ElevenLabs -> Gemini natural voice -> phone's built-in voice.
 * Fast mode: the reply is cut into sentence chunks; chunk 1 is spoken as soon as it is ready
 * while the next chunk is generated in the background, so Nova starts talking much sooner.
 */
class Speaker(private val ctx: Context, private val prefs: Prefs) {
    private class Audio(val pcm: ByteArray?, val mp3: ByteArray?)

    private var ready = false
    private val handler = Handler(Looper.getMainLooper())
    private var pendingFinish: (() -> Unit)? = null
    private val tts = TextToSpeech(ctx) { ready = (it == TextToSpeech.SUCCESS) }
    private var track: AudioTrack? = null
    private var player: MediaPlayer? = null
    private var mp3Resume: (() -> Unit)? = null
    private var gen = 0
    var onInfo: ((String) -> Unit)? = null
    private fun info(m: String) { handler.post { onInfo?.invoke(m) } }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { handler.post { pendingFinish?.invoke() } }
            override fun onError(id: String?) { handler.post { pendingFinish?.invoke() } }
        })
    }

    fun speak(raw: String, scope: CoroutineScope, force: Boolean = false, onStart: () -> Unit = {}, onDone: () -> Unit = {}) {
        if (!force && !prefs.speak) { onDone(); return }
        val text = clean(raw)
        if (text.isBlank()) { onDone(); return }
        stop()
        val my = gen
        val finish: () -> Unit = { if (my == gen) onDone() }
        val start: () -> Unit = { if (my == gen) onStart() }
        val netVoice = (prefs.elevenKey.isNotBlank() && prefs.elevenVoiceId.isNotBlank()) ||
            (prefs.naturalVoice && prefs.apiKey.isNotBlank())
        if (!netVoice || text.length <= 45) { system(text, start, finish); return }
        scope.launch {
            val chunks = splitChunks(text)
            var pending: Deferred<Audio?> = async { genAudio(chunks[0]) }
            var started = false
            for (i in chunks.indices) {
                val a = pending.await()
                if (my != gen) return@launch
                if (i + 1 < chunks.size) pending = async { genAudio(chunks[i + 1]) }
                if (a == null) {
                    if (i + 1 < chunks.size) pending.cancel()
                    val why = GeminiClient.lastTtsError
                    info("Natural voice failed" + (if (why.isNotBlank()) " ($why)" else "") + " -> phone voice")
                    system(chunks.drop(i).joinToString(" "), start, finish)
                    return@launch
                }
                if (!started) { started = true; start() }
                if (a.pcm != null) playPcm(a.pcm, my) else playMp3(a.mp3!!, my)
                if (my != gen) return@launch
            }
            finish()
        }
    }

    private suspend fun genAudio(chunk: String): Audio? {
        if (prefs.elevenKey.isNotBlank() && prefs.elevenVoiceId.isNotBlank()) {
            val b = ElevenLabs.tts(chunk, prefs)
            if (b != null) return Audio(null, b)
        }
        if (prefs.naturalVoice && prefs.apiKey.isNotBlank()) {
            val pcm = GeminiClient.tts(chunk, prefs)
            if (pcm != null && pcm.size > 2) return Audio(pcm, null)
        }
        return null
    }

    private fun splitChunks(t: String): List<String> {
        val sentences = t.split(Regex("(?<=[.!?।])\\s+")).filter { it.isNotBlank() }
        val out = ArrayList<String>()
        var cur = StringBuilder()
        for (s0 in sentences) {
            var s = s0
            while (s.length > 220) {
                if (cur.isNotEmpty()) { out.add(cur.toString().trim()); cur = StringBuilder() }
                out.add(s.take(220))
                s = s.drop(220)
            }
            val limit = if (out.isEmpty()) 110 else 220
            if (cur.isNotEmpty() && cur.length + s.length + 1 > limit) {
                out.add(cur.toString().trim()); cur = StringBuilder()
            }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(s)
        }
        if (cur.isNotBlank()) out.add(cur.toString().trim())
        return if (out.isEmpty()) listOf(t) else out
    }

    private fun system(text: String, start: () -> Unit, finish: () -> Unit) {
        if (!ready) { info("Phone voice engine not ready. Install/enable Google Text-to-speech in phone Settings."); finish(); return }
        val hindi = text.any { it in '\u0900'..'\u097F' }
        val lr = tts.setLanguage(if (hindi) Locale("hi", "IN") else Locale.getDefault())
        if (lr == TextToSpeech.LANG_MISSING_DATA || lr == TextToSpeech.LANG_NOT_SUPPORTED) {
            info("Hindi voice data missing. Settings > Text-to-speech > install Hindi voice data.")
            tts.setLanguage(Locale.getDefault())
        }
        tts.setPitch(prefs.pitch)
        tts.setSpeechRate(prefs.rate)
        pendingFinish = finish
        start()
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nova")
    }

    private suspend fun playMp3(bytes: ByteArray, my: Int) {
        suspendCancellableCoroutine<Unit> { cont ->
            try {
                val f = File(ctx.cacheDir, "nova_tts.mp3")
                f.writeBytes(bytes)
                val mp = MediaPlayer()
                mp.setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                mp.setDataSource(f.absolutePath)
                mp3Resume = { if (cont.isActive) cont.resume(Unit) }
                mp.setOnCompletionListener { if (cont.isActive) cont.resume(Unit) }
                mp.setOnErrorListener { _, _, _ -> if (cont.isActive) cont.resume(Unit); true }
                mp.prepare()
                if (my != gen) { mp.release(); if (cont.isActive) cont.resume(Unit); return@suspendCancellableCoroutine }
                player?.let { try { it.release() } catch (e: Exception) { } }
                player = mp
                mp.start()
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(Unit)
            }
        }
        mp3Resume = null
    }

    private suspend fun playPcm(pcm: ByteArray, my: Int) {
        val n = pcm.size - (pcm.size % 2)
        try {
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
            track?.let { try { it.release() } catch (e: Exception) { } }
            track = t
            t.play()
            val ms = n * 1000L / 48000L + 150L
            var waited = 0L
            while (waited < ms && my == gen) { delay(50); waited += 50 }
        } catch (e: Exception) {
        }
    }

    fun stop() {
        gen++
        handler.removeCallbacksAndMessages(null)
        pendingFinish = null
        tts.stop()
        track?.let {
            try { it.stop() } catch (e: Exception) { }
            try { it.release() } catch (e: Exception) { }
        }
        track = null
        player?.let {
            try { it.stop() } catch (e: Exception) { }
            try { it.release() } catch (e: Exception) { }
        }
        player = null
        mp3Resume?.invoke()
        mp3Resume = null
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
