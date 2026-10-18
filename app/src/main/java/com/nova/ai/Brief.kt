package com.nova.ai

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Jarvis-style daily briefing: greeting, date, weather, battery and notifications. */
object Brief {
    fun isAsk(t: String): Boolean = listOf(
        "good morning", "morning brief", "briefing", "daily brief", "सुप्रभात", "सुबह का सार", "आज का हाल", "आज का सार"
    ).any { t.contains(it) }

    suspend fun build(ctx: Context, prefs: Prefs): String {
        val name = prefs.userName
        val cal = Calendar.getInstance()
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val greet = when {
            h < 12 -> "सुप्रभात"
            h < 17 -> "नमस्ते"
            h < 21 -> "शुभ संध्या"
            else -> "शुभ रात्रि"
        }
        val date = SimpleDateFormat("EEEE, d MMMM", Locale("hi", "IN")).format(cal.time)
        val weather = if (prefs.city.isBlank()) "मौसम जानने के लिए Settings में अपना शहर डाल दो।" else Weather.summaryHi(prefs.city)
        val bat = try {
            (ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            -1
        }
        val batText = if (bat in 0..100) " बैटरी $bat% है।" else ""
        val notif = when {
            NovaNotificationService.instance == null -> "Notifications सुनने के लिए Settings में Notification access चालू कर दो।"
            NovaNotificationService.count() == 0 -> "कोई नई notification नहीं है।"
            else -> "${NovaNotificationService.count()} notifications हैं, बोलो \"notification पढ़ो\"।"
        }
        return "$greet $name! आज $date है। $weather$batText $notif"
    }

    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(ctx, 77, Intent(ctx, BriefReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Schedules the next daily briefing notification at HH:mm (empty = off). */
    fun schedule(ctx: Context, prefs: Prefs) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx))
        val m = Regex("^(\\d{1,2}):(\\d{2})$").find(prefs.briefTime.trim()) ?: return
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        if (h > 23 || min > 59) return
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, min); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, pending(ctx))
    }
}

class BriefReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val prefs = Prefs(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("nova_brief", "Nova daily brief", NotificationManager.IMPORTANCE_HIGH))
        val open = Intent(ctx, MainActivity::class.java).putExtra("brief", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(ctx, 78, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = android.app.Notification.Builder(ctx, "nova_brief")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Nova: आज का सार तैयार है")
            .setContentText("दबाओ, मैं सुनाती हूँ 💙")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(78, n)
        Brief.schedule(ctx, prefs)
    }
}
