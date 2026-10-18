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

/** Handles device commands locally. Returns Nova's reply, or null to send the text to Gemini. */
object CommandRouter {

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
