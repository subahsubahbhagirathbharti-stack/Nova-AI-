package com.nova.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Handles device commands locally. Returns Nova's reply, or null to send the text to Gemini. */
object CommandRouter {

    fun handle(ctx: Context, raw: String, name: String): String? {
        val t = raw.trim().lowercase().trimEnd('.', '!', '?')
        Regex("^(?:please )?(?:open|launch|start) (.+)$").find(t)?.let { return openApp(ctx, it.groupValues[1], name) }
        Regex("^(?:please )?(?:play|youtube|search youtube for) (.+)$").find(t)?.let { return youtube(ctx, it.groupValues[1], name) }

        val needsA11y = Regex("^(tap \\d+ \\d+|click .+|swipe (up|down|left|right)|scroll (up|down)|go back|back|go home|home|recents|notifications)$")
        if (needsA11y.matches(t)) {
            val svc = NovaAccessibilityService.instance
                ?: return "I need Accessibility permission to control the screen, $name. Settings → Enable screen control 💙"
            val ok = when {
                t.startsWith("tap ") -> t.split(" ").let { svc.tap(it[1].toFloat(), it[2].toFloat()) }
                t.startsWith("click ") -> svc.clickText(t.removePrefix("click "))
                t == "scroll down" -> svc.swipe("up")   // finger moves up to scroll down
                t == "scroll up" -> svc.swipe("down")
                t.startsWith("swipe ") -> svc.swipe(t.removePrefix("swipe "))
                t == "go back" || t == "back" -> svc.back()
                t == "go home" || t == "home" -> svc.home()
                t == "recents" -> svc.recents()
                else -> svc.notifications()
            }
            return if (ok) "Done, $name ✨" else "Hmm, I couldn't do that one. Maybe it isn't on screen?"
        }
        if (t == "accessibility settings") {
            ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return "Opening Accessibility settings."
        }
        return null
    }

    private fun openApp(ctx: Context, query: String, name: String): String {
        val pm = ctx.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
        val hit = apps.firstOrNull { it.first.equals(query, true) }
            ?: apps.firstOrNull { it.first.contains(query, true) }
            ?: return "I couldn't find an app called \"$query\", $name 😕"
        val intent = pm.getLaunchIntentForPackage(hit.second) ?: return "I can't launch ${hit.first}."
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "Opening ${hit.first} for you 💫"
    }

    private fun youtube(ctx: Context, q: String, name: String): String {
        val yt = Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube")
            .putExtra("query", q).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(yt) } catch (e: Exception) {
            ctx.startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return "Searching YouTube for \"$q\" 🎶 Tap the first result, $name — or say \"click $q\" and I'll tap it."
    }
}
