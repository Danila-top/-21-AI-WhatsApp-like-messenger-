package com.danilatop.aimessenger.tools

import kotlin.math.round

class ToolRegistry {
    fun calculate(expression: String): String {
        val cleaned = expression.replace(" ", "")
        require(cleaned.isNotBlank()) { "Выражение пустое." }
        require(cleaned.matches(Regex("[0-9+\\-*/().]+"))) {
            "Разрешены только арифметические выражения."
        }

        val value = runCatching { SimpleArithmetic(cleaned).parse() }
            .getOrElse { throw IllegalArgumentException("Не удалось вычислить: " + (it.message ?: "ошибка синтаксиса")) }

        require(value.isFinite()) { "Результат не является конечным числом." }
        return (round(value * 1_000_000) / 1_000_000).toString()
    }

    private class SimpleArithmetic(private val s: String) {
        private var i = 0

        fun parse(): Double {
            val value = addSub()
            require(i == s.length) { "Лишний символ в позиции " + i }
            return value
        }

        private fun addSub(): Double {
            var value = mulDiv()
            while (i < s.length) {
                value = when (s[i]) {
                    '+' -> { i++; value + mulDiv() }
                    '-' -> { i++; value - mulDiv() }
                    else -> return value
                }
            }
            return value
        }

        private fun mulDiv(): Double {
            var value = unary()
            while (i < s.length) {
                value = when (s[i]) {
                    '*' -> { i++; value * unary() }
                    '/' -> { i++; value / unary() }
                    else -> return value
                }
            }
            return value
        }

        private fun unary(): Double {
            if (i < s.length && s[i] == '-') {
                i++
                return -unary()
            }
            if (i < s.length && s[i] == '(') {
                i++
                val value = addSub()
                require(i < s.length && s[i] == ')') { "Не закрыты скобки." }
                i++
                return value
            }

            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            require(start != i) { "Ожидалось число в позиции " + start }
            return s.substring(start, i).toDouble()
        }
    }
}
