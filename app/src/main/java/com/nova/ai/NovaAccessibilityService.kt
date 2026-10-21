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

    /** Tap at a fraction of the screen, e.g. tapAt(0.5f, 0.3f) = middle, 30% from the top. */
    fun tapAt(fx: Float, fy: Float): Boolean {
        val dm = resources.displayMetrics
        return tap(dm.widthPixels * fx, dm.heightPixels * fy)
    }

    /** Types text into the field that currently has input focus. */
    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = android.os.Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** Presses the keyboard's Enter/Search key in the focused field (Android 11+). */
    fun pressEnter(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 30) return false
        val root = rootInActiveWindow ?: return false
        val f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        return f.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
    }

    fun back() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun recents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun notifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    /** Turns the screen off and locks the phone (Android 9+). */
    fun lockScreen(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) else false

    fun clickText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        var node: AccessibilityNodeInfo? = root.findAccessibilityNodeInfosByText(text).firstOrNull()
        if (node == null) {
            // some apps only set contentDescription
            val want = text.lowercase()
            fun find(n: AccessibilityNodeInfo?, d: Int): AccessibilityNodeInfo? {
                if (n == null || d > 25) return null
                val cd = n.contentDescription?.toString()?.lowercase()
                if (cd != null && cd.contains(want)) return n
                for (i in 0 until n.childCount) find(n.getChild(i), d + 1)?.let { return it }
                return null
            }
            node = find(root, 0)
        }
        if (node == null) return false
        var n: AccessibilityNodeInfo? = node
        while (n != null && !n.isClickable) n = n.parent
        if (n != null && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        // last resort: tap the middle of the element on screen
        val r = android.graphics.Rect()
        node.getBoundsInScreen(r)
        if (r.width() > 0 && r.height() > 0) return tap(r.exactCenterX(), r.exactCenterY())
        return false
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
