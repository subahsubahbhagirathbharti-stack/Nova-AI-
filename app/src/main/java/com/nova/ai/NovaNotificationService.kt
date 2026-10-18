package com.nova.ai

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Lets Nova read your notifications aloud. Works only after you turn on Notification access for Nova AI. */
class NovaNotificationService : NotificationListenerService() {

    companion object {
        @Volatile var instance: NovaNotificationService? = null
        private val recent = ArrayList<String>()

        fun latest(n: Int): List<String> = synchronized(recent) { recent.take(n) }
        fun count(): Int = synchronized(recent) { recent.size }
        private fun add(s: String) = synchronized(recent) {
            recent.remove(s)
            recent.add(0, s)
            while (recent.size > 30) recent.removeAt(recent.size - 1)
        }
    }

    private fun describe(sbn: StatusBarNotification): String? {
        if (sbn.packageName == packageName) return null
        val n = sbn.notification ?: return null
        if (sbn.isOngoing) return null
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val ex = n.extras ?: return null
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return null
        val app = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: Exception) {
            sbn.packageName
        }
        return "$app से: " + listOf(title, text).filter { it.isNotEmpty() }.joinToString(" — ").take(160)
    }

    override fun onListenerConnected() {
        instance = this
        try {
            activeNotifications?.sortedBy { it.postTime }?.forEach { sbn -> describe(sbn)?.let { add(it) } }
        } catch (e: Exception) {
        }
    }

    override fun onListenerDisconnected() {
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            describe(sbn)?.let { add(it) }
        } catch (e: Exception) {
        }
    }
}
