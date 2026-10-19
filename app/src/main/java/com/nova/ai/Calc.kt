package com.nova.ai

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/** Tiny offline calculator: + - * / % and brackets. */
object Calc {
    fun eval(src: String): Double? = try {
        val s = src.replace('x', '*').replace('×', '*').replace('÷', '/').replace(" ", "")
        val p = Parser(s)
        val v = p.expr()
        if (p.i != s.length) null else v
    } catch (e: Exception) {
        null
    }

    fun format(v: Double): String =
        if (v == Math.floor(v) && abs(v) < 1e15) v.toLong().toString()
        else BigDecimal(v).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    fun speakable(expr: String): String = expr.trim()
        .replace("+", " जमा ").replace("-", " माइनस ")
        .replace("*", " गुणा ").replace("×", " गुणा ").replace("x", " गुणा ")
        .replace("/", " भाग ").replace("÷", " भाग ").replace("%", " मॉड ")
        .replace(Regex("\\s+"), " ")

    private class Parser(val s: String) {
        var i = 0

        fun expr(): Double {
            var v = term()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val op = s[i++]
                val r = term()
                v = if (op == '+') v + r else v - r
            }
            return v
        }

        fun term(): Double {
            var v = factor()
            while (i < s.length && (s[i] == '*' || s[i] == '/' || s[i] == '%')) {
                val op = s[i++]
                val r = factor()
                v = when (op) {
                    '*' -> v * r
                    '/' -> v / r
                    else -> v % r
                }
            }
            return v
        }

        fun factor(): Double {
            if (i < s.length && s[i] == '-') { i++; return -factor() }
            if (i < s.length && s[i] == '+') { i++; return factor() }
            if (i < s.length && s[i] == '(') {
                i++
                val v = expr()
                if (i >= s.length || s[i] != ')') throw IllegalArgumentException("bracket")
                i++
                return v
            }
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (st == i) throw IllegalArgumentException("number")
            return s.substring(st, i).toDouble()
        }
    }
}
