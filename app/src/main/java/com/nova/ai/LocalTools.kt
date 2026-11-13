package com.nova.ai

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/** Instant offline helpers (no internet, no waiting). Used by the voice router and the Terminal. */
object LocalTools {
    private val jokes = listOf(
        "टीचर ने पूछा: पानी में कौन सी चीज़ डूबती नहीं? पप्पू बोला: मैडम, मछली! 😄",
        "पति: आज खाने में क्या बनाओगी? पत्नी: जो तुम्हें आसान लगे। पति: तो फिर ऑर्डर कर दो! 😂",
        "मोबाइल में 1% बैटरी थी। मैं बोला: बस एक ज़रूरी मैसेज भेजना है। मोबाइल बोला: मैं भी बहुत ज़रूरी हूँ, बंद हो रहा हूँ! 🔋",
        "बच्चा: पापा, इंटरनेट इतना धीमा क्यों है? पापा: बेटा, वो भी सोमवार से घबराता है। 😅",
        "दोस्त: तू इतना थका हुआ क्यों लग रहा है? मैं: रात भर सपने में परीक्षा देता रहा, नंबर अभी तक नहीं आए। 😴",
        "डॉक्टर: आपको आराम की ज़रूरत है। मरीज़: पर आराम करने का टाइम कहाँ है? डॉक्टर: मोबाइल बंद करके देखो। मरीज़: कुछ और बताइए! 📵"
    )
    private val shayari = listOf(
        "चाँद से कह दो आज ज़रा देर से निकले, मेरे दोस्त की हँसी अभी बाकी है। 🌙",
        "रास्ते मुश्किल हों तो क्या, मंज़िल को भी तो तुम्हारा इंतज़ार है।",
        "हौसले की एक चिंगारी काफ़ी है, अँधेरे को हराने के लिए। 🔥",
        "कुछ बातें लफ़्ज़ों में नहीं आतीं, बस दिल से दिल तक जाती हैं। 💙",
        "आज की थकान कल की मुस्कान बनेगी, बस चलते रहना।",
        "तेरी याद आई तो चाय भी मीठी लगी, यादों की चीनी बड़ी कमाल है। ☕"
    )
    private val quotes = listOf(
        "छोटे-छोटे कदम भी एक दिन मंज़िल तक पहुँचा देते हैं।",
        "आज का काम कल पर मत छोड़ो, कल के पास अपना काम है।",
        "गलतियाँ इस बात का सबूत हैं कि तुमने कोशिश की।",
        "मेहनत का फल देर से मिलता है, पर मीठा मिलता है।",
        "खुद पर भरोसा रखो, बाकी सब रास्ते में मिल जाएगा।",
        "थककर रुक जाओ तो आराम करो, पर हार मानकर नहीं।"
    )

    private val lenFactor = mapOf("km" to 1000.0, "m" to 1.0, "cm" to 0.01, "mile" to 1609.344, "ft" to 0.3048, "inch" to 0.0254)
    private val massFactor = mapOf("kg" to 1.0, "lb" to 0.45359237, "g" to 0.001)

    private fun unit(u: String): String? = when (u) {
        "km", "kilometer", "kilometers", "kilometre", "kilometres" -> "km"
        "m", "meter", "meters", "metre", "metres" -> "m"
        "cm", "centimeter", "centimeters" -> "cm"
        "mile", "miles" -> "mile"
        "ft", "feet", "foot" -> "ft"
        "inch", "inches" -> "inch"
        "kg", "kilo", "kilos", "kilogram", "kilograms" -> "kg"
        "lb", "lbs", "pound", "pounds" -> "lb"
        "g", "gram", "grams" -> "g"
        "c", "celsius" -> "c"
        "f", "fahrenheit" -> "f"
        else -> null
    }

    fun run(ctx: Context, t: String, name: String, prefs: Prefs): String? {
        // ---- dice, coin, random ----
        if (t in setOf("dice", "roll dice", "dice roll", "pasa phenko", "पासा फेंको", "पासा फेंक")) {
            return "पासा गिरा: " + Random.nextInt(1, 7) + " 🎲"
        }
        if (t in setOf("coin", "flip coin", "toss", "heads or tails", "सिक्का उछालो", "सिक्का फेंको")) {
            return if (Random.nextBoolean()) "छाप (Heads) 🪙" else "पट (Tails) 🪙"
        }
        Regex("^(?:random|rand|रैंडम) (\\d+) (\\d+)$").find(t)?.let {
            val a = it.groupValues[1].toInt()
            val b = it.groupValues[2].toInt()
            if (b >= a) return "रैंडम नंबर: " + Random.nextInt(a, b + 1) + " 🎯"
        }

        // ---- date and time ----
        if (t in setOf("time", "what time is it", "samay kya hua", "time kya hua", "अभी कितने बजे हैं", "कितने बजे हैं", "समय क्या हुआ", "टाइम क्या हुआ")) {
            return "अभी " + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()) + " बजे हैं ⏰"
        }
        if (t in setOf("date", "today date", "aaj ki date", "आज की तारीख", "आज कौन सा दिन है", "aaj kaun sa din hai", "day")) {
            return "आज " + SimpleDateFormat("EEEE, d MMMM yyyy", Locale("hi", "IN")).format(Date()) + " है 📅"
        }

        // ---- phone status ----
        if (t in setOf("battery", "battery status", "battery kitni hai", "बैटरी", "बैटरी कितनी है")) {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val st = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = st?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            return "बैटरी $pct% है" + (if (charging) ", चार्ज हो रही है 🔌" else " 🔋")
        }
        if (t in setOf("storage", "free storage", "storage kitna hai", "स्टोरेज", "स्टोरेज कितना है")) {
            val sf = StatFs(Environment.getDataDirectory().path)
            val free = sf.availableBytes / 1073741824.0
            val total = sf.totalBytes / 1073741824.0
            return "स्टोरेज में " + Calc.format(Math.round(free * 10) / 10.0) + " GB खाली है, कुल " + Calc.format(Math.round(total * 10) / 10.0) + " GB 💾"
        }
        if (t in setOf("ram", "free ram", "memory", "ram kitni free hai", "रैम", "रैम कितनी फ्री है")) {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            return "RAM में " + (mi.availMem / 1048576) + " MB खाली है, कुल " + (mi.totalMem / 1048576) + " MB 🧠"
        }

        // ---- fun ----
        if (t in setOf("joke", "joke sunao", "chutkula", "mazak", "जोक सुनाओ", "चुटकुला सुनाओ", "जोक")) return jokes.random()
        if (t in setOf("shayari", "shayari sunao", "शायरी सुनाओ", "शायरी")) return shayari.random()
        if (t in setOf("quote", "quote sunao", "motivation", "motivate me", "मोटिवेशन", "प्रेरणा", "कोई अच्छी बात")) return quotes.random()

        // ---- to-do list ----
        Regex("^(?:task add|todo add|add task|टास्क जोड़ो|काम जोड़ो) (.+)$").find(t)?.let {
            prefs.tasks = (prefs.tasks + "\n" + it.groupValues[1]).trim()
            return "टास्क जोड़ दिया ✅"
        }
        if (t in setOf("task list", "tasks", "todo list", "my tasks", "टास्क दिखाओ", "मेरे काम", "काम दिखाओ")) {
            val lines = prefs.tasks.lines().filter { it.isNotBlank() }
            return if (lines.isEmpty()) "अभी कोई टास्क नहीं है, $name।"
            else "तुम्हारे टास्क:\n" + lines.mapIndexed { i, s -> "" + (i + 1) + ". " + s }.joinToString("\n")
        }
        Regex("^(?:task done|done task|टास्क हो गया) (\\d+)$").find(t)?.let {
            val lines = prefs.tasks.lines().filter { l -> l.isNotBlank() }.toMutableList()
            val idx = it.groupValues[1].toInt() - 1
            if (idx in lines.indices) {
                val gone = lines.removeAt(idx)
                prefs.tasks = lines.joinToString("\n")
                return "शाबाश! \"" + gone + "\" हो गया 🎉"
            }
            return "ये नंबर लिस्ट में नहीं है, $name।"
        }
        if (t in setOf("task clear", "tasks clear", "टास्क मिटाओ")) {
            prefs.tasks = ""
            return "सारे टास्क मिटा दिए।"
        }

        // ---- focus timer ----
        if (t in setOf("pomodoro", "focus timer", "फोकस टाइमर", "पोमोडोरो")) {
            val i = Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, 1500)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "Nova focus: 25 मिनट पूरे, अब 5 मिनट आराम")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                ctx.startActivity(i)
                "25 मिनट का focus timer चालू 🍅 पूरा होने पर 5 मिनट आराम करना, $name।"
            } catch (e: Exception) {
                "Timer नहीं लग पाया, $name।"
            }
        }

        // ---- Gemini fast / smart ----
        if (t in setOf("gemini fast", "fast model", "जेमिनी फास्ट", "fast mode on", "turbo on", "turbo")) {
            prefs.turbo = true
            return "Gemini Fast चालू ⚡ अब हल्का और तेज़ model पहले चलेगा।"
        }
        if (t in setOf("gemini smart", "smart model", "जेमिनी स्मार्ट", "turbo off", "smart mode")) {
            prefs.turbo = false
            return "Gemini Smart चालू 🧠 अब बड़ा model पहले चलेगा, जवाब थोड़ा देर से पर ज़्यादा समझदार।"
        }

        // ---- unit converter ----
        Regex("^(?:convert )?(-?\\d+(?:\\.\\d+)?) ?([a-z]+) (?:to|in|se|में) ([a-z]+)$").find(t)?.let {
            val v = it.groupValues[1].toDouble()
            val a = unit(it.groupValues[2])
            val b = unit(it.groupValues[3])
            if (a != null && b != null) {
                val fa = lenFactor[a]
                val fb = lenFactor[b]
                val ma = massFactor[a]
                val mb = massFactor[b]
                val r: Double? = when {
                    a == "c" && b == "f" -> v * 9 / 5 + 32
                    a == "f" && b == "c" -> (v - 32) * 5 / 9
                    fa != null && fb != null -> v * fa / fb
                    ma != null && mb != null -> v * ma / mb
                    else -> null
                }
                if (r != null) return Calc.format(v) + " " + a + " = " + Calc.format(Math.round(r * 10000) / 10000.0) + " " + b
            }
        }
        return null
    }
}
