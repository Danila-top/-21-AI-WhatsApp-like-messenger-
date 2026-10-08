package com.danilatop.aimessenger.tools

import kotlin.math.round

class ToolRegistry {
    fun calculate(expression: String): String {
        val cleaned = expression.replace(" ", "")
        require(cleaned.matches(Regex("[0-9+\\-*/().]+"))) { "Разрешены только арифметические выражения." }
        return runCatching {
            val value = SimpleArithmetic(cleaned).parse()
            round(value * 1_000_000) / 1_000_000
        }.getOrElse { error("Не удалось вычислить: \${it.message}") }.toString()
    }

    private class SimpleArithmetic(private val s: String) {
        private var i = 0
        fun parse(): Double {
            val v = addSub()
            require(i == s.length)
            return v
        }
        private fun addSub(): Double {
            var v = mulDiv()
            while (i < s.length) {
                v = when (s[i]) {
                    '+' -> { i++; v + mulDiv() }
                    '-' -> { i++; v - mulDiv() }
                    else -> return v
                }
            }
            return v
        }
        private fun mulDiv(): Double {
            var v = unary()
            while (i < s.length) {
                v = when (s[i]) {
                    '*' -> { i++; v * unary() }
                    '/' -> { i++; v / unary() }
                    else -> return v
                }
            }
            return v
        }
        private fun unary(): Double {
            if (i < s.length && s[i] == '-') { i++; return -unary() }
            if (i < s.length && s[i] == '(') {
                i++
                val v = addSub()
                require(i < s.length && s[i] == ')')
                i++
                return v
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            require(start != i) { "Ожидалось число." }
            return s.substring(start, i).toDouble()
        }
    }
}
