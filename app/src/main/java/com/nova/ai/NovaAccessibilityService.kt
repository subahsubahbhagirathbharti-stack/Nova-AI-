package com.nova.ai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class NovaAccessibilityService : AccessibilityService() {
    companion object { @Volatile var instance: NovaAccessibilityService? = null }

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: android.content.Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun gesture(path: Path, ms: Long): Boolean =
        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, ms)).build(), null, null)

    fun tap(x: Float, y: Float) = gesture(Path().apply { moveTo(x, y) }, 60)

    fun swipe(dir: String): Boolean {
        val w = resources.displayMetrics.widthPixels.toFloat()
        val h = resources.displayMetrics.heightPixels.toFloat()
        val p = Path()
        when (dir) {
            "up" -> { p.moveTo(w / 2, h * 0.75f); p.lineTo(w / 2, h * 0.25f) }
            "down" -> { p.moveTo(w / 2, h * 0.25f); p.lineTo(w / 2, h * 0.75f) }
            "left" -> { p.moveTo(w * 0.8f, h / 2); p.lineTo(w * 0.2f, h / 2) }
            "right" -> { p.moveTo(w * 0.2f, h / 2); p.lineTo(w * 0.8f, h / 2) }
            else -> return false
        }
        return gesture(p, 300)
    }

    fun back() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun recents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun notifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    /** Clicks the first on-screen element whose text/description matches. */
    fun clickText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        var n: AccessibilityNodeInfo? = root.findAccessibilityNodeInfosByText(text).firstOrNull()
        while (n != null && !n.isClickable) n = n.parent
        return n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }
}
