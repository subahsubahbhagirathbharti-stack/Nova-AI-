package com.nova.ai

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/** Jarvis-style arc reactor. Set mode = idle | listening | thinking | speaking. */
class ArcReactorView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var mode: String = "idle"
        set(v) { field = v; invalidate() }
    private var angle = 0f
    private var last = System.nanoTime()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        val dt = ((now - last) / 1e9f).coerceAtMost(0.1f)
        last = now
        val (speed, color) = when (mode) {
            "listening" -> 90f to 0xFF00E5FF.toInt()
            "thinking" -> 240f to 0xFFFFB300.toInt()
            "speaking" -> 140f to 0xFFFF2E9A.toInt()
            else -> 25f to 0xFF008BA3.toInt()
        }
        angle = (angle + speed * dt) % 360f
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - 14f
        val pulse = 0.5f + 0.5f * Math.sin(Math.toRadians(angle * 3.0)).toFloat()

        p.style = Paint.Style.STROKE
        p.color = color
        p.setShadowLayer(18f, 0f, 0f, color)
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 3f
        p.alpha = 120
        c.drawCircle(cx, cy, r, p)

        p.alpha = 255
        p.strokeWidth = 9f
        val a = r * 0.85f
        val rect = RectF(cx - a, cy - a, cx + a, cy + a)
        c.drawArc(rect, angle, 70f, false, p)
        c.drawArc(rect, angle + 180f, 70f, false, p)

        p.strokeWidth = 4f
        p.pathEffect = DashPathEffect(floatArrayOf(6f, 14f), 0f)
        val b = r * 0.68f
        c.drawArc(RectF(cx - b, cy - b, cx + b, cy + b), -angle * 1.5f, 300f, false, p)
        p.pathEffect = null

        p.style = Paint.Style.FILL
        p.alpha = (80 + 140 * pulse).toInt()
        c.drawCircle(cx, cy, r * 0.30f * (0.9f + 0.2f * pulse), p)

        p.clearShadowLayer()
        p.color = Color.WHITE
        p.alpha = 230
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = r * 0.17f
        c.drawText("NOVA", cx, cy + p.textSize / 3f, p)

        postInvalidateOnAnimation()
    }
}
