package com.danilatop.aimessenger.tools

import com.danilatop.aimessenger.data.AppDatabase
import com.danilatop.aimessenger.data.MemoryEntity
import com.danilatop.aimessenger.data.ScheduledTaskEntity
import com.danilatop.aimessenger.data.ToolApprovalEntity
import com.danilatop.aimessenger.data.WorkspaceFileEntity
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.round

enum class ToolRisk { SAFE, CONFIRM }

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: JSONObject,
    val risk: ToolRisk = ToolRisk.SAFE
)

data class ToolCall(val id: String, val name: String, val arguments: JSONObject)

data class ToolExecution(
    val output: String,
    val requiresApproval: Boolean = false,
    val approvalId: String? = null
)

class ToolRegistry {
    fun definitions(): List<ToolDefinition> = listOf(
        ToolDefinition(
            "calculator",
            "Evaluate a basic arithmetic expression.",
            JSONObject()
                .put("type", "object")
                .put("properties", JSONObject().put(
                    "expression", JSONObject().put("type", "string")
                ))
                .put("required", JSONArray().put("expression"))
                .put("additionalProperties", false)
        ),
        ToolDefinition(
            "remember",
            "Persist an important fact in long-term memory.",
            JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()
                    .put("key", JSONObject().put("type", "string"))
                    .put("value", JSONObject().put("type", "string"))
                    .put("importance", JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 100)))
                .put("required", JSONArray().put("key").put("value").put("importance"))
                .put("additionalProperties", false)
        ),
        ToolDefinition(
            "workspace_write",
            "Create or replace a text file in the current AI workspace.",
            JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()
                    .put("name", JSONObject().put("type", "string"))
                    .put("content", JSONObject().put("type", "string")))
                .put("required", JSONArray().put("name").put("content"))
                .put("additionalProperties", false)
        ),
        ToolDefinition(
            "schedule_task",
            "Schedule a prompt for the AI agent to process later.",
            JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()
                    .put("delay_minutes", JSONObject().put("type", "integer").put("minimum", 1))
                    .put("prompt", JSONObject().put("type", "string")))
                .put("required", JSONArray().put("delay_minutes").put("prompt"))
                .put("additionalProperties", false)
        ),
        ToolDefinition(
            "request_external_action",
            "Request a consequential external action; human approval is required.",
            JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()
                    .put("action", JSONObject().put("type", "string"))
                    .put("details", JSONObject().put("type", "string")))
                .put("required", JSONArray().put("action").put("details"))
                .put("additionalProperties", false),
            ToolRisk.CONFIRM
        )
    )

    fun asOpenAITools(): JSONArray = JSONArray().apply {
        definitions().forEach { tool ->
            put(
                JSONObject()
                    .put("type", "function")
                    .put(
                        "name", tool.name
                    )
                    .put("description", tool.description)
                    .put("parameters", tool.parameters)
                    .put("strict", true)
            )
        }
    }

    suspend fun execute(db: AppDatabase, conversationId: String, call: ToolCall): ToolExecution {
        val definition = definitions().firstOrNull { it.name == call.name }
            ?: return ToolExecution("Unknown tool: " + call.name)

        if (definition.risk == ToolRisk.CONFIRM) {
            val approvalId = UUID.randomUUID().toString()
            db.approvals().upsert(
                ToolApprovalEntity(
                    approvalId,
                    conversationId,
                    call.name,
                    call.arguments.toString(),
                    "PENDING"
                )
            )
            return ToolExecution(
                "Human approval required. approval_id=" + approvalId,
                true,
                approvalId
            )
        }

        return when (call.name) {
            "calculator" -> ToolExecution(calculate(call.arguments.getString("expression")))

            "remember" -> {
                val key = call.arguments.getString("key").trim()
                val value = call.arguments.getString("value").trim()
                val importance = call.arguments.getInt("importance").coerceIn(0, 100)
                require(key.isNotBlank()) { "Memory key is empty." }
                db.memories().upsert(
                    MemoryEntity(
                        id = key.lowercase(),
                        scope = "global",
                        key = key,
                        value = value,
                        importance = importance
                    )
                )
                ToolExecution("Memory saved: " + key)
            }

            "workspace_write" -> {
                val name = call.arguments.getString("name").trim()
                val fileContent = call.arguments.getString("content")
                require(name.isNotBlank()) { "File name is empty." }
                db.files().upsert(
                    WorkspaceFileEntity(
                        id = conversationId + ":" + name,
                        conversationId = conversationId,
                        name = name,
                        content = fileContent
                    )
                )
                ToolExecution("Workspace file written: " + name)
            }

            "schedule_task" -> {
                val minutes = call.arguments.getLong("delay_minutes")
                val prompt = call.arguments.getString("prompt").trim()
                require(minutes >= 1) { "delay_minutes must be >= 1" }
                require(prompt.isNotBlank()) { "Prompt is empty." }
                val task = ScheduledTaskEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    title = prompt.take(120),
                    prompt = prompt,
                    nextRunAt = System.currentTimeMillis() + minutes * 60_000L
                )
                db.tasks().upsert(task)
                ToolExecution("Task scheduled: " + task.id)
            }

            else -> ToolExecution("Tool not implemented: " + call.name)
        }
    }

    fun calculate(expression: String): String {
        val cleaned = expression.replace(" ", "")
        require(cleaned.isNotBlank()) { "Выражение пустое." }
        require(cleaned.matches(Regex("[0-9+\-*/().]+"))) {
            "Разрешены только арифметические выражения."
        }
        val value = runCatching { SimpleArithmetic(cleaned).parse() }
            .getOrElse { throw IllegalArgumentException("Ошибка: " + (it.message ?: "syntax")) }
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
            if (i < s.length && s[i] == '-') { i++; return -unary() }
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
