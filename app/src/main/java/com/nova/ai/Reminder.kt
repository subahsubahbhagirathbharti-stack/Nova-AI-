package com.nova.ai

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/** One-time reminders that show a notification at the chosen time. */
object Reminder {
    /** Returns true only if Android accepted the alarm. */
    fun schedule(ctx: Context, text: String, at: Long): Boolean = try {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val i = Intent(ctx, ReminderReceiver::class.java).putExtra("text", text)
        val id = (at / 1000L).toInt()
        val pi = PendingIntent.getBroadcast(ctx, id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
        true
    } catch (e: Exception) {
        false
    }

    /** Next moment the clock shows h:m. If h is 1..12 and no am/pm was said, picks the sooner of AM/PM. */
    fun nextTime(h: Int, m: Int, ampm: String): Long {
        val now = System.currentTimeMillis()
        val hours = when {
            ampm == "pm" -> listOf(if (h < 12) h + 12 else h)
            ampm == "am" -> listOf(if (h == 12) 0 else h)
            h in 1..11 -> listOf(h, h + 12)
            h == 12 -> listOf(12, 0)
            else -> listOf(h)
        }
        var best = Long.MAX_VALUE
        for (hh in hours) for (day in 0..1) {
            val c = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hh); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, day)
            }
            if (c.timeInMillis > now && c.timeInMillis < best) best = c.timeInMillis
        }
        return best
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val text = intent.getStringExtra("text") ?: "याद दिलाना था"
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("nova_reminder", "Nova reminders", NotificationManager.IMPORTANCE_HIGH))
        val open = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(ctx, 79, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(ctx, "nova_reminder")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Nova याद दिला रही है ⏰")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify((System.currentTimeMillis() / 1000L).toInt(), n)
    }
}
