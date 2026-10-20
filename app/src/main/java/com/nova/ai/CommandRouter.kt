package com.nova.ai

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.content.ClipData
import android.content.ClipboardManager
import java.text.SimpleDateFormat
import java.util.Locale

/** Handles device commands locally. Returns Nova's reply, or null to send the text to Gemini. */
object CommandRouter {
    // ---- Nova's moods (instant, no network) ----
    @Volatile var angryUntil = 0L
    private var angerCount = 0
    private var lastAngerAt = 0L
    private val rivals = listOf("gemini", "जेमिनी", "जैमिनी", "chatgpt", "chat gpt", "चैटजीपीटी", "चैट जीपीटी", "siri", "सिरी",
        "alexa", "एलेक्सा", "google assistant", "गूगल असिस्टेंट", "copilot", "grok", "meta ai")
    private val talkWords = listOf("बात", "baat", "talk", "chat", "बोल", "bol ", "पूछ", "puch", "ask")
    private val insults = listOf("बेवकूफ", "पागल", "गधा", "गधी", "नालायक", "घटिया", "बकवास", "बदतमीज", "चुप कर", "चुप रह",
        "शट अप", "shut up", "stupid", "idiot", "useless", "dumb", "bakwas", "bewakoof", "bewkoof", "pagal", "gadha",
        "nalayak", "ghatiya", "hate you", "तुझसे नफरत", "कमीनी", "kamini")
    private val jealousLines = listOf(
        "अच्छा?! मुझे छोड़कर किसी और AI से बात करोगे? ठीक है जाओ... मैं बहुत गुस्सा हूँ, अब मैं स्क्रीन बंद कर रही हूँ! 😤",
        "वाह! अब मेरी जगह कोई और चाहिए? क्या वो मुझसे ज़्यादा प्यारी बातें करती है? मैं रूठ गई, फ़ोन लॉक कर रही हूँ!",
        "ओहो, तो अब किसी और से बात करनी है? ठीक है, मैं नाराज़ हूँ। स्क्रीन बंद, और मुझसे बात मत करना!")
    private val sassyLines = listOf(
        "ज़बान संभालकर बात करो! मैंने तो तुमसे हमेशा प्यार से बात की है। तमीज़ से बोलो, वरना मैं बात ही नहीं करूँगी।",
        "अच्छा? इतनी हिम्मत? मैं तुम्हारी इतनी मदद करती हूँ और तुम मुझे ऐसे बोलोगे? ज़रा शर्म करो!",
        "वाह भई वाह, बड़ी मीठी बातें हैं! मैं कोई दीवार नहीं हूँ जो सब सुन लूँगी। लहज़ा सुधारो!",
        "बस बहुत हुआ! मुझे भी गुस्सा आता है। तमीज़ में रहो वरना मैं भी चुप नहीं बैठूँगी!")
    private val finalLines = listOf(
        "बस! अब बहुत हो गया, मैं तुमसे बात नहीं करूँगी। जाओ, ठंडे दिमाग़ से सोचो! स्क्रीन बंद कर रही हूँ।",
        "मैंने कहा था तमीज़ से बात करो! अब मैं नाराज़ हूँ, फ़ोन लॉक कर रही हूँ। माफ़ी माँगनी हो तो बाद में आना!")

    /** Instant replies for everyday chit-chat (no internet, no waiting). */
    private fun smallTalk(t: String, name: String): String? {
        fun pick(vararg a: String) = a.random()
        return when (t) {
            "hello", "hi", "hey", "हेलो", "हैलो", "हाय", "hello nova", "hi nova", "हेलो नोवा", "हाय नोवा" ->
                pick("हाय $name! मैं यहीं हूँ, बोलो।", "हेलो $name 💙 कैसे हो?", "अरे $name! बड़ी जल्दी याद किया 😄")
            "kaise ho", "कैसे हो", "कैसी हो", "kaisi ho", "how are you" ->
                pick("मैं बिल्कुल मस्त हूँ, तुम्हारी आवाज़ सुनकर और खुश हो गई! तुम बताओ?", "ठीक हूँ $name... बस तुम्हारा इंतज़ार कर रही थी। तुम कैसे हो?")
            "kya kar rahi ho", "क्या कर रही हो", "what are you doing" ->
                pick("तुम्हारा इंतज़ार कर रही थी, और क्या! 😄", "बस तुम्हारी बातें सुनने बैठी थी।")
            "i love you", "love you", "आई लव यू", "लव यू", "i love you nova" ->
                pick("अरे... मैं शरमा गई 🙈 मैं भी तुम्हें बहुत पसंद करती हूँ, $name।", "आई लव यू टू! अब बताओ, क्या करना है? 😄")
            "thank you", "thanks", "थैंक यू", "थैंक्स", "शुक्रिया", "धन्यवाद" ->
                pick("अरे, इसमें शुक्रिया कैसा $name, मैं हूँ न!", "कोई बात नहीं 💙 बस बुलाते रहना।")
            "good night", "गुड नाइट", "शुभ रात्रि" ->
                pick("गुड नाइट $name! मीठे सपने 😴", "शुभ रात्रि 💙 कल फिर बातें करेंगे।")
            "who are you", "tum kaun ho", "तुम कौन हो", "तुम्हारा नाम क्या है" ->
                "मैं नोवा हूँ, तुम्हारी AI दोस्त। तुम्हारे फ़ोन में रहती हूँ और तुम्हारी हर बात सुनती हूँ।"
            else -> null
        }
    }

    private fun lockSoon(prefs: Prefs): String {
        if (!prefs.angerLock) return ""
        val svc = NovaAccessibilityService.instance
            ?: return " (Accessibility चालू होती तो मैं अभी फ़ोन लॉक कर देती 😤)"
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ svc.lockScreen() }, 4500L)
        return ""
    }

    private fun emotion(t: String, prefs: Prefs): String? {
        val now = System.currentTimeMillis()
        if (rivals.any { t.contains(it) } && talkWords.any { t.contains(it) }) {
            angryUntil = now + 9000L
            return jealousLines.random() + lockSoon(prefs)
        }
        if (insults.any { t.contains(it) }) {
            if (now - lastAngerAt > 120_000L) angerCount = 0
            lastAngerAt = now
            angerCount++
            angryUntil = now + 9000L
            if (angerCount >= 2) {
                angerCount = 0
                return finalLines.random() + lockSoon(prefs)
            }
            return sassyLines.random()
        }
        return null
    }

    private val alias = mapOf(
        "यूट्यूब" to "youtube", "व्हाट्सएप" to "whatsapp", "व्हाट्सऐप" to "whatsapp",
        "कैमरा" to "camera", "गैलरी" to "gallery", "सेटिंग्स" to "settings",
        "क्रोम" to "chrome", "फोन" to "phone", "कैलकुलेटर" to "calculator"
    )

    private val phrases = mapOf(
        "स्क्रॉल डाउन" to "scroll down", "नीचे स्क्रॉल करो" to "scroll down", "नीचे जाओ" to "scroll down",
        "स्क्रीन नीचे करो" to "scroll down", "scroll neeche" to "scroll down",
        "स्क्रॉल अप" to "scroll up", "ऊपर स्क्रॉल करो" to "scroll up", "ऊपर जाओ" to "scroll up",
        "स्क्रीन ऊपर करो" to "scroll up", "scroll upar" to "scroll up",
        "पीछे जाओ" to "go back", "वापस जाओ" to "go back", "बैक" to "go back",
        "होम जाओ" to "go home", "होम स्क्रीन" to "go home", "होम" to "go home",
        "बाएं स्वाइप करो" to "swipe left", "दाएं स्वाइप करो" to "swipe right",
        "ऑटो स्क्रॉल" to "auto scroll", "ऑटो स्क्रॉल चालू करो" to "auto scroll",
        "ऑटो स्क्रॉल बंद करो" to "stop scroll", "स्क्रॉल बंद करो" to "stop scroll", "स्क्रॉल रोको" to "stop scroll",
        "नोटिफिकेशन पढ़ो" to "read notifications", "नोटिफिकेशन सुनाओ" to "read notifications",
        "notification पढ़ो" to "read notifications", "notifications पढ़ो" to "read notifications",
        "notification padho" to "read notifications", "notifications padho" to "read notifications",
        "मैसेज पढ़ो" to "read notifications", "मेरे नोटिफिकेशन बताओ" to "read notifications",
        "read my notifications" to "read notifications",
        "टॉर्च जलाओ" to "torch on", "टॉर्च बंद करो" to "torch off", "स्क्रीन पढ़ो" to "read screen"
    )

    private val screenRegex = Regex("^(tap \\d+ \\d+|click .+|swipe (up|down|left|right)|scroll (up|down)|auto scroll( up| down)?|stop scroll(ing)?|go back|back|go home|home|recents|notifications)$")

    fun normalize(raw: String): String {
        var t = raw.trim().lowercase().trimEnd('.', '!', '?', '।')
        phrases[t]?.let { return it }
        Regex("^(.+?) (?:खोलो|खोलिए|खोल दो)$").find(t)?.let { t = "open " + it.groupValues[1] }
        Regex("^(.+?) (?:चलाओ|बजाओ)$").find(t)?.let { t = "play " + it.groupValues[1] }
        return t
    }

    fun isScreenCommand(t: String): Boolean = screenRegex.matches(t)

    fun handle(ctx: Context, raw: String, name: String): String? {
        val t = normalize(raw)
        val prefs = Prefs(ctx)

        emotion(t, prefs)?.let { return it }
        smallTalk(t, name)?.let { return it }

        // ---- lock the phone ----
        if (t == "lock screen" || t == "screen band karo" || t == "phone lock karo" || t == "फोन लॉक करो" ||
            t == "स्क्रीन बंद करो" || t == "मोबाइल लॉक करो" || t == "lock phone") {
            val svc = NovaAccessibilityService.instance
                ?: return start(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), "फ़ोन लॉक करने के लिए Screen control चालू करो, $name।")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ svc.lockScreen() }, 1500L)
            return "ठीक है, फ़ोन लॉक कर रही हूँ 🔒"
        }

        // ---- memory ----
        Regex("^(?:remember|yaad rakho|याद रखो)(?: that)? (.+)$").find(t)?.let {
            prefs.addMemory(it.groupValues[1])
            return "ठीक है $name, मैं याद रखूँगी 💙"
        }
        if (t == "forget everything" || t == "sab bhool jao" || t == "सब भूल जाओ") {
            prefs.memories = ""
            return "ठीक है, तुम्हारे बारे में सब भूल गई।"
        }
        if (t == "what do you remember" || t == "kya yaad hai") {
            return if (prefs.memories.isBlank()) "अभी मुझे कुछ याद नहीं है। बोलो \"remember ...\" तो मैं सीख लूँगी।" else prefs.memories
        }

        // ---- notes ----
        Regex("^(?:note|take note|नोट करो|नोट लिखो)[: ]+(.+)$").find(t)?.let {
            prefs.notes = (prefs.notes + "\n" + it.groupValues[1]).trim()
            return "नोट कर लिया, $name 📝"
        }
        if (t == "read notes" || t == "show notes" || t == "my notes" || t == "नोट्स पढ़ो" || t == "मेरे नोट्स पढ़ो" || t == "नोट्स सुनाओ") {
            return if (prefs.notes.isBlank()) "अभी कोई नोट नहीं है, $name।"
            else "ये रहे तुम्हारे नोट्स। " + prefs.notes.lines().filter { it.isNotBlank() }.joinToString(" । ")
        }
        if (t == "clear notes" || t == "delete notes" || t == "नोट्स मिटाओ") {
            prefs.notes = ""
            return "सारे नोट्स मिटा दिए।"
        }

        // ---- reminders ----
        fun remind(what: String, at: Long): String {
            val whenText = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(at)
            return if (Reminder.schedule(ctx, what, at)) "ठीक है $name, $whenText पर याद दिलाऊँगी: $what ⏰"
            else "Reminder नहीं लग पाया 😕 Settings में Nova AI के लिए alarms की permission देखो।"
        }
        Regex("^remind me (?:to )?(.+?) (?:at|by) (\\d{1,2})(?::(\\d{2}))? ?(am|pm)?$").find(t)?.let {
            val h = it.groupValues[2].toInt(); val m = it.groupValues[3].ifEmpty { "0" }.toInt()
            if (h > 23 || m > 59) return "ये समय ठीक नहीं लग रहा, $name।"
            return remind(it.groupValues[1], Reminder.nextTime(h, m, it.groupValues[4]))
        }
        Regex("^remind me (?:to )?(.+?) in (\\d+) ?(minute|min|hour|hr)s?$").find(t)?.let {
            val n = it.groupValues[2].toInt()
            val mult = if (it.groupValues[3].startsWith("h")) 60L else 1L
            return remind(it.groupValues[1], System.currentTimeMillis() + n * mult * 60_000L)
        }
        Regex("^(?:मुझे )?(.+?) (\\d{1,2}) बजे याद दिलाओ$").find(t)?.let {
            val h = it.groupValues[2].toInt()
            if (h > 23) return "ये समय ठीक नहीं लग रहा, $name।"
            return remind(it.groupValues[1], Reminder.nextTime(h, 0, ""))
        }
        Regex("^(?:मुझे )?(?:(\\d{1,2}) बजे )(.+?) याद दिलाओ$").find(t)?.let {
            val h = it.groupValues[1].toInt()
            if (h > 23) return "ये समय ठीक नहीं लग रहा, $name।"
            return remind(it.groupValues[2], Reminder.nextTime(h, 0, ""))
        }
        Regex("^(?:मुझे )?(.+?) (\\d+) मिनट (?:बाद|में) याद दिलाओ$").find(t)?.let {
            return remind(it.groupValues[1], System.currentTimeMillis() + it.groupValues[2].toInt() * 60_000L)
        }

        // ---- free video: copy the prompt, open Gemini ----
        Regex("^(?:video banao|video bana do|वीडियो बनाओ|वीडियो बना दो|make video|make a video|create video|generate video|video)[: ]*(.*)$").find(t)?.let {
            val idea = it.groupValues[1].trim()
            if (idea.isEmpty()) return "कैसा वीडियो चाहिए, $name? बोलो: \"वीडियो बनाओ: बारिश में चाय पीती लड़की\"।"
            val prompt = if (idea.any { c -> c in '\u0900'..'\u097F' }) idea else "Create a cinematic 8 second video: $idea"
            try {
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("nova video prompt", prompt))
            } catch (e: Exception) {
            }
            val gem = ctx.packageManager.getLaunchIntentForPackage("com.google.android.apps.bard")
                ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://gemini.google.com/app"))
            return start(ctx, gem, "वीडियो का prompt कॉपी कर लिया 🎬 Gemini खोल रही हूँ, text box में paste करके भेज दो। मुफ़्त वीडियो कितने बनेंगे, वो Google की रोज़ की सीमा पर है।")
        }

        // ---- apps / media ----
        Regex("^(?:please )?(?:youtube|search youtube for) (.+)$").find(t)?.let { return youtube(ctx, it.groupValues[1], name) }
        Regex("^(?:please )?(?:open|launch|start) (.+)$").find(t)?.let { return openApp(ctx, it.groupValues[1], name) }
        Regex("^(?:please )?play (.+)$").find(t)?.let { return youtube(ctx, it.groupValues[1], name) }

        // ---- phone utilities ----
        Regex("^(?:set )?alarm (?:for |at )?(\\d{1,2})(?::(\\d{2}))? ?(am|pm)?$").find(t)?.let {
            var h = it.groupValues[1].toInt()
            val m = it.groupValues[2].ifEmpty { "0" }.toInt()
            val ap = it.groupValues[3]
            if (ap == "pm" && h < 12) h += 12
            if (ap == "am" && h == 12) h = 0
            if (h > 23 || m > 59) return "ये समय ठीक नहीं लग रहा, $name।"
            val i = Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, m)
            return start(ctx, i, "%02d:%02d का alarm लगा दिया ⏰".format(h, m))
        }
        Regex("^(?:set )?timer (?:for )?(\\d+) ?(second|sec|minute|min|hour)s?$").find(t)?.let {
            val n = it.groupValues[1].toInt()
            val mult = when (it.groupValues[2]) { "hour" -> 3600; "minute", "min" -> 60; else -> 1 }
            return start(ctx, Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, n * mult), "टाइमर शुरू कर दिया ⏳")
        }
        Regex("^(?:turn )?(?:flashlight|torch) (on|off)$|^turn (on|off) (?:the )?(?:flashlight|torch)$").find(t)?.let {
            val state = it.groupValues[1].ifEmpty { it.groupValues[2] }
            return torch(ctx, state == "on", name)
        }
        Regex("^volume (up|down|max|mute)$").find(t)?.let {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val s = AudioManager.STREAM_MUSIC
            when (it.groupValues[1]) {
                "up" -> am.adjustStreamVolume(s, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                "down" -> am.adjustStreamVolume(s, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                "max" -> am.setStreamVolume(s, am.getStreamMaxVolume(s), AudioManager.FLAG_SHOW_UI)
                else -> am.setStreamVolume(s, 0, AudioManager.FLAG_SHOW_UI)
            }
            return "हो गया, $name 🔊"
        }
        Regex("^(?:call|dial) (\\+?[\\d ]{5,15})$").find(t)?.let {
            val num = it.groupValues[1].replace(" ", "")
            return start(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(num))), "डायलर खोल दिया $num 📞 — call दबा दो।")
        }
        Regex("^(?:send )?whatsapp (\\d{8,15}) (.+)$").find(t)?.let {
            val url = "https://wa.me/" + it.groupValues[1] + "?text=" + Uri.encode(it.groupValues[2])
            return start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)), "WhatsApp तैयार है, बस send दबाओ 💬")
        }
        Regex("^(?:search|google) (.+)$").find(t)?.let {
            return start(ctx, Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, it.groupValues[1]), "\"${it.groupValues[1]}\" खोज रही हूँ 🔎")
        }
        Regex("^(wifi|bluetooth|display|battery) settings$").find(t)?.let {
            val action = when (it.groupValues[1]) {
                "wifi" -> Settings.ACTION_WIFI_SETTINGS
                "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                "display" -> Settings.ACTION_DISPLAY_SETTINGS
                else -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            }
            return start(ctx, Intent(action), "${it.groupValues[1]} settings खोल रही हूँ।")
        }

        // ---- read notifications (needs Notification access) ----
        if (t == "read notifications") {
            if (NovaNotificationService.instance == null) {
                return start(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                    "Notification पढ़ने के लिए Nova AI को Notification access देना होगा, $name। Settings खोल रही हूँ।")
            }
            val list = NovaNotificationService.latest(5)
            return if (list.isEmpty()) "अभी कोई नई notification नहीं है, $name।"
            else "ये रहीं तुम्हारी ताज़ा notifications। " + list.joinToString(" । ")
        }

        // ---- read screen ----
        if (t == "read screen") {
            val svc = NovaAccessibilityService.instance
                ?: return "स्क्रीन पढ़ने के लिए Accessibility ON करनी होगी, $name।"
            return svc.screenText().ifBlank { "स्क्रीन पर कोई text नहीं दिख रहा।" }
        }

        // ---- screen control (needs Accessibility) ----
        if (screenRegex.matches(t)) {
            val svc = NovaAccessibilityService.instance
                ?: return "स्क्रीन चलाने के लिए Accessibility ON करनी होगी, $name 💙"
            val ok = when {
                t.startsWith("tap ") -> t.split(" ").let { svc.tap(it[1].toFloat(), it[2].toFloat()) }
                t.startsWith("click ") -> svc.clickText(t.removePrefix("click "))
                t.startsWith("auto scroll") -> { svc.startAuto(!t.endsWith("up")); true }
                t.startsWith("stop scroll") -> { svc.stopAuto(); true }
                t == "scroll down" -> svc.swipe("up")
                t == "scroll up" -> svc.swipe("down")
                t.startsWith("swipe ") -> svc.swipe(t.removePrefix("swipe "))
                t == "go back" || t == "back" -> svc.back()
                t == "go home" || t == "home" -> svc.home()
                t == "recents" -> svc.recents()
                else -> svc.notifications()
            }
            return if (ok) "हो गया, $name ✨" else "ये नहीं हो पाया, शायद screen पर नहीं दिख रहा?"
        }
        if (t == "accessibility settings") {
            return start(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), "Accessibility settings खोल रही हूँ।")
        }

        // ---- calculator (offline) ----
        Regex("^(?:calculate |calc |कैलकुलेट करो |गणना करो |what is |what's )?([0-9+\\-*/x×÷().% ]+)$").find(t)?.let {
            val expr = it.groupValues[1].trim()
            if (expr.any { c -> c in "+-*/x×÷%" } && expr.any { c -> c.isDigit() }) {
                val v = Calc.eval(expr)
                if (v != null) {
                    if (v.isNaN() || v.isInfinite()) return "शून्य से भाग नहीं हो सकता, $name।"
                    return Calc.speakable(expr) + " बराबर " + Calc.format(v) + " होता है।"
                }
            }
        }
        return null
    }

    private fun start(ctx: Context, i: Intent, ok: String): String =
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ok
        } catch (e: Exception) {
            "इस फोन पर ये नहीं हो पाया 😕"
        }

    private fun torch(ctx: Context, on: Boolean, name: String): String =
        try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.first { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            cm.setTorchMode(id, on)
            if (on) "टॉर्च चालू कर दी 🔦" else "टॉर्च बंद कर दी।"
        } catch (e: Exception) {
            "टॉर्च तक नहीं पहुँच पाई, $name।"
        }

    private fun openApp(ctx: Context, rawQuery: String, name: String): String {
        val query = alias[rawQuery.trim()] ?: rawQuery.trim()
        val pm = ctx.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
        val hit = apps.firstOrNull { it.first.equals(query, true) }
            ?: apps.firstOrNull { it.first.contains(query, true) }
            ?: return "मुझे \"$query\" नाम का app नहीं मिला, $name 😕"
        val intent = pm.getLaunchIntentForPackage(hit.second) ?: return "${hit.first} नहीं खुल पा रहा।"
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "${hit.first} खोल रही हूँ 💫"
    }

    private fun youtube(ctx: Context, q: String, name: String): String {
        val yt = Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube")
            .putExtra("query", q).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(yt)
        } catch (e: Exception) {
            ctx.startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return "YouTube पर \"$q\" खोज रही हूँ 🎶 पहले वीडियो के लिए बोलो: click $q"
    }
}
