package com.nova.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

/**
 * Nova's face: an original animated anime-style girl drawn in code (no image or model files needed).
 * Set mode = idle | listening | thinking | speaking. She floats, blinks, her mouth moves while speaking,
 * and the glow / effects change with every state.
 */
class AvatarView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var mode: String = "idle"
        set(v) { field = v; invalidate() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val r = RectF()
    private val t0 = System.nanoTime()
    private val dst = RectF()
    private val src = android.graphics.Rect()
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val lite: Boolean = Prefs(ctx).liteUi
    private var circleBmp: Bitmap? = null
    private var circleD = 0
    private val photo: Bitmap? = try {
        BitmapFactory.decodeResource(resources, R.drawable.nova_avatar)
    } catch (e: Throwable) {
        null
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        circleBmp = null
        val pic = photo ?: return
        val u = minOf(h / 100f, w / 64f)
        val d = minOf(w - 12f, 90f * u).toInt()
        if (d <= 0) return
        try {
            val side = minOf((pic.width * 0.80f).toInt(), pic.height)
            val sx = ((pic.width - side) / 2f).toInt()
            val out = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
            val cv = Canvas(out)
            val pth = Path()
            pth.addCircle(d / 2f, d / 2f, d / 2f, Path.Direction.CW)
            cv.clipPath(pth)
            cv.drawBitmap(pic, android.graphics.Rect(sx, 0, sx + side, side), RectF(0f, 0f, d.toFloat(), d.toFloat()), bmpPaint)
            circleBmp = out
            circleD = d
        } catch (e: Throwable) {
            circleBmp = null
        }
    }

    private fun fill(color: Int, alpha: Int = 255) {
        p.style = Paint.Style.FILL
        p.color = color
        p.alpha = alpha
    }

    private fun stroke(color: Int, width: Float, alpha: Int = 255) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.color = color
        p.strokeWidth = width
        p.alpha = alpha
    }

    override fun onDraw(c: Canvas) {
        val t = (System.nanoTime() - t0) / 1e9f
        val u = minOf(height / 100f, width / 64f)
        if (u <= 0f) return
        val cx = width / 2f
        val y0 = (height - 100f * u) / 2f
        val bob = if (lite) 0f else sin(t * 1.6f) * 0.9f * u
        val speaking = mode == "speaking"
        val angry = System.currentTimeMillis() < CommandRouter.angryUntil
        val glow = if (angry) 0xFFFF3B30.toInt() else when (mode) {
            "listening" -> 0xFF00E5FF.toInt()
            "thinking" -> 0xFFFFB300.toInt()
            "speaking" -> 0xFFFF2E9A.toInt()
            else -> 0xFF7B5CFF.toInt()
        }
        val hair = 0xFFFF8FC8.toInt()
        val hairDark = 0xFFFF6FB5.toInt()
        val skin = 0xFFFFE6D8.toInt()
        fun X(v: Float) = cx + v * u
        fun Y(v: Float) = y0 + v * u + bob

        // aura
        val pulse = 1f + 0.04f * sin(t * (if (speaking) 9f else 3f))
        for (i in 0 until (if (lite) 2 else 5)) {
            fill(glow, 14 + i * 10)
            c.drawCircle(cx, Y(48f), (46f - i * 6f) * u * pulse, p)
        }

        val pic = photo
        if (pic != null) {
            val d = minOf(width - 12f, 90f * u)
            val rad = d / 2f
            val cyy = y0 + 50f * u + bob
            val sc = if (speaking) 1f + 0.015f * sin(t * 8f) else 1f
            val side = minOf((pic.width * 0.80f).toInt(), pic.height)
            val sx = ((pic.width - side) / 2f).toInt()
            src.set(sx, 0, sx + side, side)
            dst.set(cx - rad, cyy - rad, cx + rad, cyy + rad)
            c.save()
            c.scale(sc, sc, cx, cyy)
            val cb = circleBmp
            if (cb != null) {
                c.drawBitmap(cb, null, dst, bmpPaint)
            } else {
                path.reset()
                path.addCircle(cx, cyy, rad, Path.Direction.CW)
                c.clipPath(path)
                c.drawBitmap(pic, src, dst, bmpPaint)
            }
            c.restore()
            stroke(glow, 1.4f * u, if (speaking) (170 + 85 * (0.5f + 0.5f * sin(t * 8f))).toInt() else 210)
            c.drawCircle(cx, cyy, rad * sc, p)
        } else {
        // back hair + twin tails
        fill(hairDark)
        r.set(X(-27f), Y(12f), X(27f), Y(60f)); c.drawOval(r, p)
        r.set(X(-26f), Y(40f), X(26f), Y(92f)); c.drawRoundRect(r, 12f * u, 12f * u, p)
        r.set(X(-37f), Y(26f), X(-22f), Y(84f)); c.drawOval(r, p)
        r.set(X(22f), Y(26f), X(37f), Y(84f)); c.drawOval(r, p)

        // jacket + inner top + neck
        fill(0xFFF3EEFA.toInt())
        r.set(X(-21f), Y(70f), X(21f), Y(112f)); c.drawRoundRect(r, 8f * u, 8f * u, p)
        fill(0xFF2A2140.toInt())
        r.set(X(-8f), Y(72f), X(8f), Y(112f)); c.drawRoundRect(r, 3f * u, 3f * u, p)
        fill(0xFFFFD9C7.toInt())
        r.set(X(-4f), Y(56f), X(4f), Y(72f)); c.drawRect(r, p)

        // face + blush
        fill(skin)
        r.set(X(-17f), Y(22f), X(17f), Y(62f)); c.drawOval(r, p)
        fill(0xFFFF8FA3.toInt(), 110)
        r.set(X(-14f), Y(50f), X(-7f), Y(54f)); c.drawOval(r, p)
        r.set(X(7f), Y(50f), X(14f), Y(54f)); c.drawOval(r, p)

        // bangs
        fill(hair)
        path.reset()
        path.moveTo(X(-19f), Y(41f))
        path.quadTo(X(-20f), Y(11f), X(0f), Y(11f))
        path.quadTo(X(20f), Y(11f), X(19f), Y(41f))
        path.lineTo(X(12f), Y(30f))
        path.lineTo(X(6f), Y(38f))
        path.lineTo(X(0f), Y(29f))
        path.lineTo(X(-6f), Y(38f))
        path.lineTo(X(-12f), Y(30f))
        path.close()
        c.drawPath(path, p)

        // eyes
        val blink = (t % 4.2f) > 4.05f
        val open = if (blink) 0.08f else 1f
        val lookY = if (mode == "thinking") -1.2f * u else 0f
        val lookX = if (mode == "thinking") 1.0f * u else 0f
        for (sx in floatArrayOf(-8f, 8f)) {
            val ex = X(sx)
            val ey = Y(44f)
            val h = 5.6f * u * open
            fill(0xFFFFFFFF.toInt())
            r.set(ex - 4.6f * u, ey - h, ex + 4.6f * u, ey + h); c.drawOval(r, p)
            fill(0xFF8A5CFF.toInt())
            r.set(ex - 3.3f * u + lookX, ey - h * 0.92f + lookY, ex + 3.3f * u + lookX, ey + h * 0.92f + lookY); c.drawOval(r, p)
            fill(0xFF2A1450.toInt())
            r.set(ex - 1.6f * u + lookX, ey - h * 0.5f + lookY, ex + 1.6f * u + lookX, ey + h * 0.5f + lookY); c.drawOval(r, p)
            if (!blink) {
                fill(0xFFFFFFFF.toInt())
                c.drawCircle(ex - 1.2f * u + lookX, ey - h * 0.4f + lookY, 0.9f * u, p)
            }
            stroke(0xFF5A2A4A.toInt(), 0.7f * u)
            r.set(ex - 4.8f * u, ey - h - 0.4f * u, ex + 4.8f * u, ey + h)
            c.drawArc(r, 200f, 140f, false, p)
            val side = if (sx < 0) 1f else -1f
            c.drawLine(X(sx - 4f * side), Y(36.5f), X(sx + 4f * side), Y(36f), p)
        }

        // mouth
        if (speaking) {
            val o = (0.5f + 0.5f * sin(t * 13f)) * 2.6f + 0.5f
            fill(0xFF7A2A3A.toInt())
            r.set(X(-2.6f), Y(54.5f) - o * u * 0.5f, X(2.6f), Y(54.5f) + o * u * 0.5f); c.drawOval(r, p)
        } else {
            stroke(0xFFB5475E.toInt(), 0.6f * u)
            r.set(X(-3f), Y(52f), X(3f), Y(56.5f)); c.drawArc(r, 20f, 140f, false, p)
        }

        // hair clips
        fill(0xFF8A5CFF.toInt())
        c.drawCircle(X(-18f), Y(24f), 2.2f * u, p)
        c.drawCircle(X(18f), Y(24f), 2.2f * u, p)

        }

        // state effects
        when (mode) {
            "listening" -> for (i in 0 until 3) {
                val ph = (t * 0.9f + i / 3f) % 1f
                stroke(0xFF00E5FF.toInt(), 0.8f * u, ((1f - ph) * 170f).toInt())
                c.drawCircle(cx, Y(40f), (30f + ph * 20f) * u, p)
            }
            "thinking" -> for (i in 0 until 3) {
                val b = abs(sin(t * 5f + i)) * 3f
                fill(0xFFFFB300.toInt())
                c.drawCircle(X(-6f + 6f * i), Y(6f - b), 1.4f * u, p)
            }
            "speaking" -> for (i in 0 until 7) {
                val h = (2f + (0.5f + 0.5f * sin(t * 10f + i * 1.3f)) * 6f) * u
                val left = X(-18f + i * 6f)
                val bottom = y0 + 99f * u
                fill(0xFFFF2E9A.toInt(), 200)
                r.set(left, bottom - h, left + 3f * u, bottom); c.drawRoundRect(r, u, u, p)
            }
        }
        if (!lite) postInvalidateOnAnimation()
        else if (mode != "idle" || angry) postInvalidateDelayed(90)
        else postInvalidateDelayed(1000)
    }
}
