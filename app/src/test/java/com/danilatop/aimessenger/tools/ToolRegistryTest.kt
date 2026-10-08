package com.danilatop.aimessenger.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ToolRegistryTest {
    private val tools = ToolRegistry()

    @Test
    fun calculate_obeys_operator_precedence() {
        assertEquals("14.0", tools.calculate("2+3*4"))
    }

    @Test
    fun calculate_supports_parentheses() {
        assertEquals("20.0", tools.calculate("(2+3)*4"))
    }

    @Test
    fun calculate_rejects_non_arithmetic_input() {
        assertThrows(IllegalArgumentException::class.java) {
            tools.calculate("2+system")
        }
    }
}
