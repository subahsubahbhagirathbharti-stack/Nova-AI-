package com.nova.ai

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class NovaWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val v = RemoteViews(ctx.packageName, R.layout.widget_nova)
            v.setOnClickPendingIntent(R.id.wTalk, pi(ctx, "voice", 1))
            v.setOnClickPendingIntent(R.id.wCode, pi(ctx, "code", 2))
            mgr.updateAppWidget(id, v)
        }
    }
    private fun pi(ctx: Context, extra: String, code: Int) = PendingIntent.getActivity(
        ctx, code,
        Intent(ctx, MainActivity::class.java).putExtra(extra, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
