package com.nova.ai

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Optional: start listening for "Nova" after the phone restarts (experimental, depends on Android version and phone brand). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs(ctx).autoStart) return
        if (Build.VERSION.SDK_INT >= 35) return
        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, ListenService::class.java))
        } catch (e: Exception) {
        }
    }
}
