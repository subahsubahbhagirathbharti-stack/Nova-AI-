package com.nova.ai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class NovaAccessibilityService : AccessibilityService() {
    companion object { @Volatile var instance: NovaAccessibilityService? = null }

    private val h = Handler(Looper.getMainLooper())
    private var autoDir: String? = null
    private val autoRun = object : Runnable {
        override fun run() {
            val d = autoDir ?: return
            swipe(d)
            h.postDelayed(this, 3500)
        }
    }

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: android.content.Intent?): Boolean { instance = null; stopAuto(); return super.onUnbind(intent) }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun gesture(path: Path, ms: Long): Boolean =
        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, ms)).build(), null, null)

    fun tap(x: Float, y: Float) = gesture(Path().apply { moveTo(x, y) }, 60)

    fun swipe(dir: String): Boolean {
        val w = resources.displayMetrics.widthPixels.toFloat()
        val hgt = resources.displayMetrics.heightPixels.toFloat()
        val p = Path()
        when (dir) {
            "up" -> { p.moveTo(w / 2, hgt * 0.75f); p.lineTo(w / 2, hgt * 0.25f) }
            "down" -> { p.moveTo(w / 2, hgt * 0.25f); p.lineTo(w / 2, hgt * 0.75f) }
            "left" -> { p.moveTo(w * 0.8f, hgt / 2); p.lineTo(w * 0.2f, hgt / 2) }
            "right" -> { p.moveTo(w * 0.2f, hgt / 2); p.lineTo(w * 0.8f, hgt / 2) }
            else -> return false
        }
        return gesture(p, 300)
    }

    /** Keeps scrolling every few seconds until stopAuto() is called. */
    fun startAuto(scrollDown: Boolean) {
        stopAuto()
        autoDir = if (scrollDown) "up" else "down"
        h.post(autoRun)
    }

    fun stopAuto() {
        autoDir = null
        h.removeCallbacks(autoRun)
    }

    fun back() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun recents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun notifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun clickText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        var n: AccessibilityNodeInfo? = root.findAccessibilityNodeInfosByText(text).firstOrNull()
        while (n != null && !n.isClickable) n = n.parent
        return n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }

    fun screenText(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo?, d: Int) {
            if (n == null || sb.length > 2000 || d > 25) return
            val s = n.text?.toString() ?: n.contentDescription?.toString()
            if (!s.isNullOrBlank()) sb.append(s).append(" | ")
            for (i in 0 until n.childCount) walk(n.getChild(i), d + 1)
        }
        walk(root, 0)
        return sb.toString()
    }
}
