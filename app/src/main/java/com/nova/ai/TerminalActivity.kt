package com.nova.ai

import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Nova Terminal: 5 tabs, built-in commands. It is NOT a Linux shell, it runs Nova's own commands safely. */
class TerminalActivity : AppCompatActivity() {
    companion object {
        val buffers = Array(5) { StringBuilder() }
        var tab = 0
    }

    private lateinit var prefs: Prefs
    private lateinit var root: LinearLayout
    private lateinit var title: TextView
    private lateinit var themeBtn: TextView
    private lateinit var out: TextView
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var runBtn: TextView
    private lateinit var prompt: TextView
    private val tabViews = ArrayList<TextView>()
    private val ui = Handler(Looper.getMainLooper())

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        prefs = Prefs(this)
        build()
        if (buffers[tab].isEmpty()) {
            emit("NOVA TERMINAL — 5 tabs (T1..T5)")
            emit("help लिखो, सारी कमांड दिखेंगी।")
        }
        applyColors()
        render()
    }

    private fun build() {
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.fitsSystemWindows = true
        setContentView(root)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(14), dp(10), dp(14), dp(4))
        title = TextView(this)
        title.text = "NOVA TERMINAL"
        title.textSize = 16f
        title.typeface = Typeface.create("monospace", Typeface.BOLD)
        head.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        themeBtn = TextView(this)
        themeBtn.text = "THEME"
        themeBtn.textSize = 12f
        themeBtn.setPadding(dp(12), dp(8), dp(12), dp(8))
        themeBtn.setOnClickListener { Themes.showPicker(this, prefs) { applyColors() } }
        head.addView(themeBtn)
        root.addView(head)

        val tabRow = LinearLayout(this)
        tabRow.orientation = LinearLayout.HORIZONTAL
        tabRow.setPadding(dp(8), dp(4), dp(8), dp(4))
        for (i in 0 until 5) {
            val tv = TextView(this)
            tv.text = "T" + (i + 1)
            tv.gravity = Gravity.CENTER
            tv.typeface = Typeface.MONOSPACE
            tv.setPadding(0, dp(9), 0, dp(9))
            tv.setOnClickListener {
                tab = i
                applyColors()
                render()
            }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dp(3), 0, dp(3), 0)
            tabRow.addView(tv, lp)
            tabViews.add(tv)
        }
        root.addView(tabRow)

        scroll = ScrollView(this)
        out = TextView(this)
        out.typeface = Typeface.MONOSPACE
        out.textSize = 13f
        out.setTextIsSelectable(true)
        out.setPadding(dp(14), dp(8), dp(14), dp(8))
        scroll.addView(out)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(8), dp(6), dp(8), dp(8))
        prompt = TextView(this)
        prompt.text = "❯"
        prompt.textSize = 18f
        prompt.setPadding(dp(8), 0, dp(8), 0)
        row.addView(prompt)
        input = EditText(this)
        input.typeface = Typeface.MONOSPACE
        input.setSingleLine(true)
        input.hint = "help"
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        input.setPadding(dp(12), dp(10), dp(12), dp(10))
        input.setOnEditorActionListener { _, _, _ -> go(); true }
        row.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        runBtn = TextView(this)
        runBtn.text = "RUN"
        runBtn.gravity = Gravity.CENTER
        runBtn.textSize = 13f
        runBtn.typeface = Typeface.DEFAULT_BOLD
        runBtn.setPadding(dp(16), dp(11), dp(16), dp(11))
        runBtn.setOnClickListener { go() }
        val rl = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rl.setMargins(dp(8), 0, 0, 0)
        row.addView(runBtn, rl)
        root.addView(row)
    }

    private fun applyColors() {
        val p = Themes.current(prefs)
        root.background = Themes.gradient(p)
        title.setTextColor(p.accent)
        themeBtn.background = Themes.box(p.panel, p.accent, 14f)
        themeBtn.setTextColor(p.accent)
        for (i in tabViews.indices) {
            val sel = i == tab
            tabViews[i].background = Themes.box(if (sel) p.accent else p.panel, p.accent, 10f)
            tabViews[i].setTextColor(if (sel) p.bg2 else p.text)
        }
        out.setTextColor(p.text)
        prompt.setTextColor(p.accent)
        input.background = Themes.box(p.panel, p.accent, 14f)
        input.setTextColor(p.text)
        input.setHintTextColor(p.hint)
        runBtn.background = Themes.box(p.accent, p.accent2, 14f)
        runBtn.setTextColor(p.bg2)
    }

    private fun render() {
        out.text = buffers[tab].toString()
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun emit(line: String) {
        val b = buffers[tab]
        b.append(line).append('\n')
        if (b.length > 20000) b.delete(0, b.length - 15000)
    }

    private fun go() {
        val line = input.text.toString().trim()
        if (line.isEmpty()) return
        input.text.clear()
        exec(line)
    }

    private fun exec(line: String) {
        emit("❯ $line")
        val parts = line.split(" ", limit = 2)
        val head = parts[0].lowercase()
        val rest = if (parts.size > 1) parts[1].trim() else ""
        when (head) {
            "help" -> help()
            "clear", "cls" -> buffers[tab].setLength(0)
            "theme" -> themeCmd(rest)
            "themes" -> themeCmd("list")
            "ask" -> askGemini(rest)
            "js" -> runJs(rest)
            "codelab" -> startActivity(Intent(this, CodeLabActivity::class.java).apply { if (rest.isNotEmpty()) putExtra("auto", rest) })
            "sysinfo", "neofetch" -> sysinfo()
            "echo" -> emit(rest)
            "exit" -> finish()
            else -> {
                val r = CommandRouter.handle(this, line, prefs.userName)
                emit(r ?: "कमांड नहीं मिली: $head (help लिखो)")
            }
        }
        render()
    }

    private fun help() {
        emit("""
            |ask <सवाल>        Gemini से पूछो
            |js <code>         JavaScript चलाओ (सुरक्षित डिब्बे में)
            |codelab <idea>    Code Lab खोलो; idea दो तो Nova कोड लिखकर चला देगी
            |theme list | theme <नाम> | theme custom #bg #accent #accent2
            |sysinfo           फ़ोन की जानकारी
            |clear | exit | echo <text>
            |
            |और ये भी चलते हैं (वही जो Nova सुनती है):
            |calc 12*5   convert 5 km to mile   time   date
            |battery   storage   ram   dice   coin   random 1 100
            |joke   shayari   quote   pomodoro
            |task add <काम>   task list   task done 1   task clear
            |gemini fast | gemini smart
            |open youtube   torch on   weather   note: <text>   read notes
        """.trimMargin())
    }

    private fun themeCmd(arg: String) {
        val a = arg.trim()
        if (a.isEmpty() || a == "list") {
            emit("Themes: " + Themes.list.joinToString(", ") { it.name } + ", Custom")
            emit("अभी: " + prefs.themeName)
            return
        }
        if (a.startsWith("custom")) {
            val c = a.removePrefix("custom").trim().split(" ").filter { it.isNotBlank() }
            if (c.size != 3) {
                emit("ऐसे लिखो: theme custom #101820 #FFB300 #FF4081")
                return
            }
            try {
                for (x in c) android.graphics.Color.parseColor(x)
                prefs.customBg = c[0]
                prefs.customAccent = c[1]
                prefs.customAccent2 = c[2]
                prefs.themeName = "Custom"
                applyColors()
                emit("Custom theme लग गई ✅")
            } catch (e: Exception) {
                emit("रंग का कोड ग़लत है। #RRGGBB लिखो।")
            }
            return
        }
        val hit = Themes.list.firstOrNull { it.name.lowercase().contains(a.lowercase()) }
        if (hit == null) {
            emit("ऐसी theme नहीं मिली। theme list लिखो।")
            return
        }
        prefs.themeName = hit.name
        applyColors()
        emit(hit.name + " लग गई ✅")
    }

    private fun sysinfo() {
        emit("Phone   : " + Build.MANUFACTURER + " " + Build.MODEL)
        emit("Android : " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
        emit("CPU     : " + (Build.SUPPORTED_ABIS.firstOrNull() ?: "?"))
        emit(LocalTools.run(this, "ram", prefs.userName, prefs) ?: "")
        emit(LocalTools.run(this, "storage", prefs.userName, prefs) ?: "")
        emit("Theme   : " + prefs.themeName + " | Gemini mode: " + (if (prefs.turbo) "Fast" else "Smart"))
    }

    private fun askGemini(q: String) {
        if (q.isEmpty()) {
            emit("ऐसे लिखो: ask भारत की राजधानी क्या है")
            return
        }
        emit("⏳ सोच रही हूँ…")
        render()
        val myTab = tab
        lifecycleScope.launch {
            val r = GeminiClient.chat(this@TerminalActivity, listOf(Msg("user", q)), prefs, false)
            buffers[myTab].append(r).append('\n')
            if (myTab == tab) render()
        }
    }

    /** Runs JavaScript inside a locked WebView: no files, no internet, no access to the app. Stops after 4 seconds. */
    private fun runJs(code: String) {
        if (code.isEmpty()) {
            emit("ऐसे लिखो: js 2+2   या   js [1,2,3].map(x=>x*2)")
            return
        }
        val myTab = tab
        val wv = WebView(this)
        val st = wv.settings
        st.javaScriptEnabled = true
        st.allowFileAccess = false
        st.allowContentAccess = false
        st.blockNetworkLoads = true
        var done = false
        fun finishJs(text: String) {
            if (done) return
            done = true
            buffers[myTab].append(text).append('\n')
            if (myTab == tab) render()
            try { wv.destroy() } catch (e: Throwable) { }
        }
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val wrapped = "(function(){try{return String(eval(" + JSONObject.quote(code) + "));}catch(e){return 'Error: '+e.message;}})()"
                view?.evaluateJavascript(wrapped) { res ->
                    val txt = try { JSONArray("[" + res + "]").optString(0) } catch (e: Exception) { res ?: "" }
                    finishJs("= " + txt)
                }
            }
        }
        wv.loadDataWithBaseURL(null, "<html><body></body></html>", "text/html", "utf-8", null)
        ui.postDelayed({ finishJs("⏱ बहुत देर चला, रोक दिया।") }, 4000L)
    }
}
