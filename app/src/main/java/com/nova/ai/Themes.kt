package com.nova.ai

import android.app.Activity
import android.app.AlertDialog
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class Palette(
    val name: String, val bg1: Int, val bg2: Int, val accent: Int, val accent2: Int,
    val text: Int, val hint: Int, val panel: Int
)

/** 14 ready themes + a Custom theme made from your own colours. */
object Themes {
    private fun p(n: String, b1: String, b2: String, a: String, a2: String, t: String, h: String, pn: String) =
        Palette(n, Color.parseColor(b1), Color.parseColor(b2), Color.parseColor(a), Color.parseColor(a2),
            Color.parseColor(t), Color.parseColor(h), Color.parseColor(pn))

    val list: List<Palette> = listOf(
        p("Luxury Gold", "#1C1608", "#050403", "#D4AF37", "#F5E6A8", "#FFF6DC", "#A89868", "#1F1A0C"),
        p("Purple Neon", "#240F52", "#07030F", "#B57CFF", "#FF4FD8", "#F2EEFF", "#9A90B8", "#1A0F33"),
        p("Cyber Blue", "#0A1F44", "#030812", "#00E5FF", "#2979FF", "#E6F7FF", "#7FA5C0", "#0C2247"),
        p("Emerald", "#07271C", "#020B08", "#2EE59D", "#B9F6CA", "#E8FFF4", "#7FB8A0", "#0A2F22"),
        p("Crimson", "#2A0610", "#0A0204", "#FF3B5C", "#FF8A80", "#FFEDEF", "#B88A92", "#330A16"),
        p("Rose Gold", "#2B1620", "#0B0508", "#E8B4B8", "#FFD6DA", "#FFF1F2", "#B99A9E", "#321A25"),
        p("Ocean", "#06283D", "#020B12", "#4FC3F7", "#80DEEA", "#E1F5FE", "#7FA8B8", "#08324D"),
        p("Sunset", "#3A1210", "#0D0405", "#FF8A3D", "#FF4F7B", "#FFF0E6", "#C09A88", "#42170F"),
        p("Midnight", "#0B0F2E", "#02030B", "#7C8CFF", "#B39DFF", "#EEF0FF", "#8A90C0", "#10163F"),
        p("Mono Black", "#151515", "#000000", "#FFFFFF", "#BDBDBD", "#F5F5F5", "#8A8A8A", "#1E1E1E"),
        p("Matrix", "#001A08", "#000500", "#00FF66", "#B2FF59", "#CCFFDD", "#4FA070", "#00260F"),
        p("Ice", "#0D2230", "#030A10", "#B3E5FC", "#E1F5FE", "#F2FBFF", "#8FB0C0", "#12303F"),
        p("Lava", "#2B0A00", "#0A0200", "#FF5722", "#FFC107", "#FFF3E0", "#B58A70", "#3A1000"),
        p("Light", "#F6F2FF", "#E3DBF7", "#6A3DE8", "#D81B8C", "#1B1530", "#6E6690", "#FFFFFF")
    )

    @Volatile var active: Palette = list[0]

    private fun blend(a: Int, b: Int, r: Float): Int {
        val ir = 1f - r
        return Color.rgb(
            (Color.red(a) * ir + Color.red(b) * r).toInt(),
            (Color.green(a) * ir + Color.green(b) * r).toInt(),
            (Color.blue(a) * ir + Color.blue(b) * r).toInt()
        )
    }

    private fun custom(prefs: Prefs): Palette {
        val bg = Color.parseColor(prefs.customBg)
        val ac = Color.parseColor(prefs.customAccent)
        val ac2 = Color.parseColor(prefs.customAccent2)
        val lum = (0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)) / 255.0
        val dark = lum < 0.5
        val text = if (dark) Color.parseColor("#F2F2F2") else Color.parseColor("#1A1A1A")
        val hint = blend(text, bg, 0.45f)
        val bg2 = if (dark) blend(bg, Color.BLACK, 0.6f) else blend(bg, Color.BLACK, 0.12f)
        val panel = if (dark) blend(bg, Color.WHITE, 0.08f) else blend(bg, Color.WHITE, 0.6f)
        return Palette("Custom", bg, bg2, ac, ac2, text, hint, panel)
    }

    fun current(prefs: Prefs): Palette {
        val pal = if (prefs.themeName == "Custom") {
            try { custom(prefs) } catch (e: Exception) { list[0] }
        } else {
            list.firstOrNull { it.name == prefs.themeName } ?: list[0]
        }
        active = pal
        return pal
    }

    fun dp(v: Float): Float = v * Resources.getSystem().displayMetrics.density

    fun gradient(p: Palette): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(p.bg1, p.bg2))

    fun box(fill: Int, stroke: Int, radiusDp: Float, strokeDp: Float = 1.2f): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = dp(radiusDp)
        g.setStroke(dp(strokeDp).toInt().coerceAtLeast(1), stroke)
        return g
    }

    fun circle(p: Palette): GradientDrawable {
        val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(p.accent, p.accent2))
        g.shape = GradientDrawable.OVAL
        g.setStroke(dp(2f).toInt(), blend(p.accent, Color.WHITE, 0.6f))
        return g
    }

    /** One dialog: pick a ready theme, or choose Custom and type three colours. */
    fun showPicker(act: Activity, prefs: Prefs, onChanged: () -> Unit) {
        val names = ArrayList<String>()
        for (t in list) names.add(t.name)
        names.add("Custom (अपने रंग)")
        AlertDialog.Builder(act).setTitle("Theme चुनो")
            .setItems(names.toTypedArray()) { _, i ->
                if (i < list.size) {
                    prefs.themeName = list[i].name
                    Toast.makeText(act, list[i].name + " चुन ली", Toast.LENGTH_SHORT).show()
                    onChanged()
                } else {
                    showCustom(act, prefs, onChanged)
                }
            }.show()
    }

    private fun showCustom(act: Activity, prefs: Prefs, onChanged: () -> Unit) {
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        val pad = dp(16f).toInt()
        box.setPadding(pad, pad, pad, 0)
        fun field(label: String, value: String): EditText {
            val tv = TextView(act)
            tv.text = label
            box.addView(tv)
            val et = EditText(act)
            et.setText(value)
            et.setSingleLine(true)
            box.addView(et)
            return et
        }
        val bg = field("Background रंग (जैसे #101820)", prefs.customBg)
        val ac = field("Accent रंग (जैसे #FFB300)", prefs.customAccent)
        val ac2 = field("दूसरा accent रंग (जैसे #FF4081)", prefs.customAccent2)
        AlertDialog.Builder(act).setTitle("Custom theme").setView(box)
            .setPositiveButton("Save") { _, _ ->
                try {
                    val a = bg.text.toString().trim()
                    val b = ac.text.toString().trim()
                    val c = ac2.text.toString().trim()
                    Color.parseColor(a); Color.parseColor(b); Color.parseColor(c)
                    prefs.customBg = a
                    prefs.customAccent = b
                    prefs.customAccent2 = c
                    prefs.themeName = "Custom"
                    onChanged()
                } catch (e: Exception) {
                    Toast.makeText(act, "रंग का कोड ग़लत है। #RRGGBB लिखो, जैसे #FFB300", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
