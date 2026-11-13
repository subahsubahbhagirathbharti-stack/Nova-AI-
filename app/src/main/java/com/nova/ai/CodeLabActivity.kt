package com.nova.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Code Lab: Nova writes a small web app (HTML + JavaScript) and runs it straight away in a locked box
 * (no internet, no files, no access to Nova). Only HTML/JavaScript can run here, not Python or Kotlin.
 */
class CodeLabActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private lateinit var root: LinearLayout
    private lateinit var prompt: EditText
    private lateinit var code: EditText
    private lateinit var web: WebView
    private lateinit var console: TextView
    private lateinit var consoleScroll: ScrollView
    private val buttons = ArrayList<TextView>()
    private lateinit var title: TextView

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        prefs = Prefs(this)
        build()
        setupWeb()
        applyColors()
        intent.getStringExtra("auto")?.let {
            prompt.setText(it)
            writeAndRun()
        }
    }

    private fun button(label: String, onClick: () -> Unit): TextView {
        val tv = TextView(this)
        tv.text = label
        tv.gravity = Gravity.CENTER
        tv.textSize = 12f
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.setPadding(dp(8), dp(11), dp(8), dp(11))
        tv.setOnClickListener { onClick() }
        buttons.add(tv)
        return tv
    }

    private fun build() {
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.fitsSystemWindows = true
        root.setPadding(dp(10), dp(8), dp(10), dp(8))
        setContentView(root)

        title = TextView(this)
        title.text = "CODE LAB  ·  Nova लिखेगी और चलाएगी"
        title.textSize = 15f
        title.typeface = Typeface.DEFAULT_BOLD
        title.setPadding(dp(4), dp(2), dp(4), dp(6))
        root.addView(title)

        prompt = EditText(this)
        prompt.hint = "क्या बनाना है? जैसे: snake game, digital घड़ी, calculator"
        prompt.minLines = 2
        prompt.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        prompt.setPadding(dp(12), dp(10), dp(12), dp(10))
        root.addView(prompt)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, dp(6), 0, dp(6))
        fun lp(): LinearLayout.LayoutParams {
            val l = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            l.setMargins(dp(3), 0, dp(3), 0)
            return l
        }
        row.addView(button("WRITE+RUN") { writeAndRun() }, lp())
        row.addView(button("RUN") { runHtml(code.text.toString()) }, lp())
        row.addView(button("STOP") { stopRun() }, lp())
        row.addView(button("COPY") { copyCode() }, lp())
        root.addView(row)

        web = WebView(this)
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.5f))

        consoleScroll = ScrollView(this)
        console = TextView(this)
        console.typeface = Typeface.MONOSPACE
        console.textSize = 11f
        console.setTextIsSelectable(true)
        console.setPadding(dp(8), dp(6), dp(8), dp(6))
        consoleScroll.addView(console)
        val clp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(90))
        clp.setMargins(0, dp(6), 0, dp(6))
        root.addView(consoleScroll, clp)

        code = EditText(this)
        code.typeface = Typeface.MONOSPACE
        code.textSize = 11f
        code.gravity = Gravity.TOP
        code.hint = "यहाँ HTML/JavaScript कोड आएगा। चाहो तो बदलकर RUN दबाओ।"
        code.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        code.setPadding(dp(10), dp(8), dp(10), dp(8))
        root.addView(code, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun applyColors() {
        val p = Themes.current(prefs)
        root.background = Themes.gradient(p)
        title.setTextColor(p.accent)
        prompt.background = Themes.box(p.panel, p.accent, 14f)
        prompt.setTextColor(p.text)
        prompt.setHintTextColor(p.hint)
        code.background = Themes.box(p.panel, p.accent2, 14f)
        code.setTextColor(p.text)
        code.setHintTextColor(p.hint)
        console.setTextColor(p.accent)
        consoleScroll.background = Themes.box(p.bg2, p.accent, 10f, 1f)
        for (b in buttons) {
            b.background = Themes.box(p.accent, p.accent2, 14f)
            b.setTextColor(p.bg2)
        }
    }

    private fun setupWeb() {
        val st = web.settings
        st.javaScriptEnabled = true
        st.allowFileAccess = false
        st.allowContentAccess = false
        st.domStorageEnabled = false
        st.blockNetworkLoads = true
        st.setSupportMultipleWindows(false)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                log(m.messageLevel().toString() + ": " + m.message() + "  (line " + m.lineNumber() + ")")
                return true
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult): Boolean {
                log("alert: " + message)
                result.confirm()
                return true
            }
        }
    }

    private fun log(line: String) {
        console.append(line + "\n")
        consoleScroll.post { consoleScroll.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    private fun writeAndRun() {
        val q = prompt.text.toString().trim()
        if (q.isEmpty()) {
            Toast.makeText(this, "पहले लिखो कि क्या बनाना है", Toast.LENGTH_SHORT).show()
            return
        }
        console.text = ""
        log("✍️ Nova कोड लिख रही है…")
        lifecycleScope.launch {
            val html = GeminiClient.generateCode(prefs, q)
            if (html.startsWith("Gemini error")) {
                log(html)
            } else {
                code.setText(html)
                log("✅ कोड तैयार, चला रही हूँ…")
                runHtml(html)
            }
        }
    }

    private fun runHtml(html: String) {
        if (html.isBlank()) {
            Toast.makeText(this, "चलाने के लिए कोड नहीं है", Toast.LENGTH_SHORT).show()
            return
        }
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    private fun stopRun() {
        web.loadUrl("about:blank")
        log("⏹ रोक दिया")
    }

    private fun copyCode() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("nova code", code.text.toString()))
        Toast.makeText(this, "कोड कॉपी हो गया", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        try { web.destroy() } catch (e: Throwable) { }
        super.onDestroy()
    }
}
