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
    companion object {
        /** After a Gemini/ElevenLabs voice failure, skip the network voice for a while so Nova answers instantly. */
        @Volatile var netBlockedUntil = 0L
        @Volatile var geminiBlockedUntil = 0L
    }

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

    fun speak(raw: String, scope: CoroutineScope, force: Boolean = false, natural: Boolean = false, onStart: () -> Unit = {}, onDone: () -> Unit = {}) {
        if (!force && !prefs.speak) { onDone(); return }
        val text = clean(raw)
        if (text.isBlank()) { onDone(); return }
        stop()
        val my = gen
        val finish: () -> Unit = { if (my == gen) onDone() }
        val start: () -> Unit = { if (my == gen) onStart() }
        val now0 = System.currentTimeMillis()
        val netVoice = now0 > netBlockedUntil &&
            ((prefs.elevenKey.isNotBlank() && prefs.elevenVoiceId.isNotBlank()) ||
                (prefs.naturalVoice && prefs.apiKey.isNotBlank() && now0 > geminiBlockedUntil) ||
                prefs.gtts)
        if (!netVoice || text.length <= 45 || (prefs.fastVoice && !natural)) { system(text, start, finish); return }
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
                    if (i == 0) netBlockedUntil = System.currentTimeMillis() + 10 * 60 * 1000L
                    info("Natural voice failed" + (if (why.isNotBlank()) " ($why)" else "") + " -> phone voice for 10 min")
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
        if (prefs.naturalVoice && prefs.apiKey.isNotBlank() && System.currentTimeMillis() > geminiBlockedUntil) {
            val pcm = GeminiClient.tts(chunk, prefs)
            if (pcm != null && pcm.size > 2) return Audio(pcm, null)
            geminiBlockedUntil = System.currentTimeMillis() + 10 * 60 * 1000L
            val why = GeminiClient.lastTtsError
            info("Gemini voice failed" + (if (why.isNotBlank()) " ($why)" else "") + " -> backup voice for 10 min")
        }
        if (prefs.gtts) {
            val mp3 = GTts.tts(chunk)
            if (mp3 != null) return Audio(null, mp3)
        }
        return null
    }

    private fun splitChunks(t: String): List<String> {
        val sentences = t.split(Regex("(?<=[.!?।])\\s+")).filter { it.isNotBlank() }
        val out = ArrayList<String>()
        var cur = StringBuilder()
        for (s0 in sentences) {
            var s = s0
            while (s.length > 180) {
                if (cur.isNotEmpty()) { out.add(cur.toString().trim()); cur = StringBuilder() }
                out.add(s.take(180))
                s = s.drop(180)
            }
            val limit = if (out.isEmpty()) 110 else 180
            if (cur.isNotEmpty() && cur.length + s.length + 1 > limit) {
                out.add(cur.toString().trim()); cur = StringBuilder()
            }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(s)
        }
        if (cur.isNotBlank()) out.add(cur.toString().trim())
        return if (out.isEmpty()) listOf(t) else out
    }

    private fun localeFor(text: String): Locale {
        for (ch in text) {
            when (ch) {
                in '\u0900'..'\u097F' -> return Locale("hi", "IN")
                in '\u0980'..'\u09FF' -> return Locale("bn", "IN")
                in '\u0A00'..'\u0A7F' -> return Locale("pa", "IN")
                in '\u0A80'..'\u0AFF' -> return Locale("gu", "IN")
                in '\u0B80'..'\u0BFF' -> return Locale("ta", "IN")
                in '\u0C00'..'\u0C7F' -> return Locale("te", "IN")
                in '\u0C80'..'\u0CFF' -> return Locale("kn", "IN")
                in '\u0D00'..'\u0D7F' -> return Locale("ml", "IN")
                in '\u0600'..'\u06FF' -> return Locale("ur", "IN")
            }
        }
        return Locale.getDefault()
    }

    private fun system(text: String, start: () -> Unit, finish: () -> Unit) {
        if (!ready) { info("Phone voice engine not ready. Install/enable Google Text-to-speech in phone Settings."); finish(); return }
        val loc = localeFor(text)
        val lr = tts.setLanguage(loc)
        if (lr == TextToSpeech.LANG_MISSING_DATA || lr == TextToSpeech.LANG_NOT_SUPPORTED) {
            info("${loc.displayLanguage} voice data missing. Settings > Text-to-speech > install voice data.")
            tts.setLanguage(Locale.getDefault())
        }
        tts.setPitch(prefs.pitch)
        tts.setSpeechRate(prefs.rate)
        if (prefs.sysVoice.isNotBlank()) {
            try {
                tts.voices?.firstOrNull { it.name == prefs.sysVoice }?.let { if (it.locale.language == loc.language) tts.voice = it }
            } catch (e: Exception) { }
        }
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
