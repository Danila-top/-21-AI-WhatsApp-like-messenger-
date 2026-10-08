package com.danilatop.aimessenger.agent

import android.content.Context
import com.danilatop.aimessenger.ai.AIProvider
import com.danilatop.aimessenger.ai.AgentProfileCatalog
import com.danilatop.aimessenger.ai.AgentSpec
import com.danilatop.aimessenger.ai.ChatTurn
import com.danilatop.aimessenger.ai.DefaultAgents
import com.danilatop.aimessenger.data.ActivityLogEntity
import com.danilatop.aimessenger.data.AppDatabase
import com.danilatop.aimessenger.data.ConversationEntity
import com.danilatop.aimessenger.data.MemoryEntity
import com.danilatop.aimessenger.data.MessageEntity
import com.danilatop.aimessenger.data.ScheduledTaskEntity
import com.danilatop.aimessenger.data.ToolApprovalEntity
import com.danilatop.aimessenger.data.WorkspaceFileEntity
import com.danilatop.aimessenger.tools.ToolExecution
import com.danilatop.aimessenger.tools.ToolRegistry
import com.danilatop.aimessenger.tools.ToolCall
import com.danilatop.aimessenger.tasks.TaskScheduler
import com.danilatop.aimessenger.mcp.McpRegistry
import com.danilatop.aimessenger.security.SecureStore
import org.json.JSONObject
import java.util.UUID
import kotlin.math.max

class AgentRuntime(
    private val context: Context,
    private val db: AppDatabase,
    private val provider: AIProvider,
    private val tools: ToolRegistry = ToolRegistry(context),
    private val mcp: McpRegistry = McpRegistry(db, SecureStore(context))
) {
    private suspend fun agents(): Map<String, AgentSpec> {
        AgentProfileCatalog.ensureDefaults(db)
        return db.agentProfiles()
            .enabled()
            .associate { profile ->
                profile.id to AgentProfileCatalog.toSpec(profile)
            }
    }

    suspend fun createConversation(
        title: String = "Новый AI-чат",
        agentsCsv: String = "coordinator"
    ): String {
        val id = UUID.randomUUID().toString()
        val availableAgents = agents()
        db.conversations().upsert(
            ConversationEntity(
                id = id,
                title = title,
                participantAgentIds = sanitizeParticipants(
                    agentsCsv,
                    availableAgents.keys
                )
            )
        )
        log(id, "system", "conversation.created", "participants=" + agentsCsv)
        return id
    }

    suspend fun send(conversationId: String, input: String, agentId: String = "coordinator"): Result<String> {
        if (input.isBlank()) return Result.failure(IllegalArgumentException("Пустое сообщение."))

        db.messages().insert(
            MessageEntity(UUID.randomUUID().toString(), conversationId, "human", "human", input)
        )
        log(conversationId, "human", "message.sent", input.take(200))

        return try {
            val result = when {
                input.startsWith("/calc ") -> {
                    val resultText = tools.calculate(input.removePrefix("/calc "))
                    saveAssistant(conversationId, "calculator", resultText)
                    resultText
                }

                input.startsWith("/remember ") -> {
                    val raw = input.removePrefix("/remember ")
                    val pair = raw.split("=", limit = 2)
                    require(pair.size == 2) { "Формат: /remember ключ = значение" }
                    db.memories().upsert(
                        MemoryEntity(
                            id = pair[0].trim().lowercase(),
                            scope = "global",
                            key = pair[0].trim(),
                            value = pair[1].trim(),
                            importance = 80
                        )
                    )
                    log(conversationId, "coordinator", "memory.saved", raw.take(200))
                    "Запомнил: " + pair[0].trim() + "."
                }

                input.startsWith("/note ") -> createNote(conversationId, input.removePrefix("/note "))

                input.startsWith("/task ") -> createTask(conversationId, input.removePrefix("/task "))

                input.startsWith("/autonomous ") -> toggleAutonomous(
                    conversationId,
                    input.removePrefix("/autonomous ").trim().lowercase()
                )

                input.startsWith("/approve ") -> resolveApproval(
                    conversationId,
                    input.removePrefix("/approve ").trim(),
                    "APPROVED"
                )

                input.startsWith("/deny ") -> resolveApproval(
                    conversationId,
                    input.removePrefix("/deny ").trim(),
                    "DENIED"
                )

                input.startsWith("/team ") -> team(conversationId, input.removePrefix("/team "))

                else -> {
                    val conversation = db.conversations().get(conversationId)
                    if (conversation?.autonomous == true) {
                        autonomousOneAgent(
                            conversationId,
                            input,
                            agents()[agentId] ?: DefaultAgents.coordinator
                        )
                    } else {
                        oneAgent(
                            conversationId,
                            input,
                            agents()[agentId] ?: DefaultAgents.coordinator
                        )
                    }
                }
            }
            Result.success(result)
        } catch (e: Throwable) {
            val message = "Ошибка агента: " + (e.message ?: "неизвестная ошибка")
            saveAssistant(conversationId, "system", message)
            log(conversationId, "system", "execution.failed", message.take(300))
            Result.failure(e)
        }
    }

    private suspend fun oneAgent(
        conversationId: String,
        input: String,
        agent: AgentSpec
    ): String {
        log(conversationId, agent.id, "agent.thinking", "model=" + agent.model)
        val history = db.messages().recent(conversationId, 20).reversed()
        val memories = db.memories().recent(12)
        val memoryPrefix = if (memories.isEmpty()) "" else {
            "\nLong-term memory:\n" +
                memories.joinToString("\n") { "- " + it.key + ": " + it.value }
        }

        val turns = history.map {
            ChatTurn(
                if (it.senderType == "human") "user" else "assistant",
                it.content
            )
        }.toMutableList()

        turns.removeAll { it.role == "user" && it.content == input }
        turns += ChatTurn("user", input + memoryPrefix)

        val availableTools = tools.definitions() + mcp.definitions()

        val answer = provider.generateWithTools(
            agent = agent,
            turns = turns,
            tools = availableTools,
            execute = { call ->
                val result = if (call.name.startsWith("mcp_")) {
                    executeMcpWithPermission(conversationId, call)
                } else {
                    tools.execute(db, conversationId, call)
                }
                log(
                    conversationId,
                    agent.id,
                    "tool." + call.name,
                    result.output.take(500)
                )
                result
            }
        )
        saveAssistant(conversationId, agent.id, answer)
        log(conversationId, agent.id, "agent.completed", "chars=" + answer.length)
        return answer
    }

    private suspend fun executeMcpWithPermission(
        conversationId: String,
        call: ToolCall
    ): ToolExecution {
        val permission = db.toolPermissions().get(conversationId, call.name)?.mode
        return when (permission) {
            "DENY" -> ToolExecution(
                "MCP tool denied by this conversation's permission policy: " + call.name
            )
            "AUTO" -> mcp.executeApproved(call)
            else -> mcp.execute(conversationId, call)
        }
    }

    private suspend fun autonomousOneAgent(
        conversationId: String,
        input: String,
        agent: AgentSpec
    ): String {
        val autonomousInstruction =
            "AUTONOMOUS MODE: reason in explicit steps, identify useful tools or sub-tasks, " +
            "and propose the next concrete action. Never claim an external action happened unless the app actually executed it."
        return oneAgent(conversationId, autonomousInstruction + "\n\nUser task:\n" + input, agent)
    }

    private suspend fun team(conversationId: String, input: String): String {
        val conversation = db.conversations().get(conversationId)
        val agentMap = agents()
        val participantIds = conversation?.participantAgentIds
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        val requested = participantIds
            .mapNotNull { agentMap[it] }
            .filter { it.id != DefaultAgents.coordinator.id }
            .ifEmpty { listOf(DefaultAgents.deepseek, DefaultAgents.claude, DefaultAgents.gemini) }

        val reports = mutableListOf<String>()
        for (agent in requested) {
            try {
                val report = oneAgent(conversationId, input, agent)
                reports += "### " + agent.name + "\n" + report
            } catch (e: Throwable) {
                reports += "### " + agent.name + "\nFAILED: " + (e.message ?: "unknown")
            }
        }

        val synthesis = buildString {
            append("You are the lead coordinator of an AI messenger room. ")
            append("Synthesize independent agent reports into one rigorous answer. ")
            append("Do not hide disagreements; distinguish facts, assumptions and uncertainty.\n\n")
            append(reports.joinToString("\n\n"))
            append("\n\nOriginal task: ").append(input)
        }

        val final = provider.generate(
            DefaultAgents.coordinator,
            listOf(ChatTurn("user", synthesis))
        )
        saveAssistant(conversationId, DefaultAgents.coordinator.id, final)
        log(
            conversationId,
            "coordinator",
            "team.completed",
            "agents=" + requested.joinToString(",") { it.id }
        )
        return final
    }

    private suspend fun createNote(conversationId: String, payload: String): String {
        val pair = payload.split("|", limit = 2)
        require(pair.size == 2) { "Формат: /note имя-файла.txt | содержимое" }
        val name = pair[0].trim()
        val content = pair[1]
        require(name.isNotBlank()) { "Имя файла пустое." }

        db.files().upsert(
            WorkspaceFileEntity(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                name = name,
                content = content
            )
        )
        log(conversationId, "human", "workspace.file_created", name)
        val answer = "Файл создан в workspace: " + name
        saveAssistant(conversationId, "workspace", answer)
        return answer
    }

    private suspend fun createTask(conversationId: String, payload: String): String {
        val pair = payload.split("|", limit = 2)
        require(pair.size == 2) { "Формат: /task минуты | задача" }
        val minutes = pair[0].trim().toLongOrNull()
        require(minutes != null && minutes >= 1) { "Минуты должны быть целым числом ≥ 1." }

        val task = ScheduledTaskEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            title = pair[1].trim().take(120),
            prompt = pair[1].trim(),
            nextRunAt = System.currentTimeMillis() + max(1L, minutes) * 60_000L
        )
        db.tasks().upsert(task)
        TaskScheduler.schedule(context, task.id, minutes)
        log(conversationId, "human", "task.scheduled", task.title)
        val answer = "Задача поставлена в WorkManager и запланирована на +" + minutes + " мин."
        saveAssistant(conversationId, "scheduler", answer)
        return answer
    }

    private suspend fun toggleAutonomous(conversationId: String, state: String): String {
        require(state == "on" || state == "off") { "Используйте /autonomous on или /autonomous off." }
        db.conversations().setAutonomous(
            conversationId,
            state == "on",
            System.currentTimeMillis()
        )
        log(conversationId, "human", "autonomy.changed", state)
        val answer = if (state == "on") {
            "Автономный режим включён."
        } else {
            "Автономный режим выключен."
        }
        saveAssistant(conversationId, "system", answer)
        return answer
    }

    suspend fun approveTool(conversationId: String, approvalId: String): Result<String> =
        runCatching { resolveApproval(conversationId, approvalId, "APPROVED") }

    suspend fun denyTool(conversationId: String, approvalId: String): Result<String> =
        runCatching { resolveApproval(conversationId, approvalId, "DENIED") }

    private suspend fun resolveApproval(
        conversationId: String,
        approvalId: String,
        status: String
    ): String {
        require(approvalId.isNotBlank()) { "Укажите id заявки." }
        val existing = db.approvals().get(approvalId)
            ?: error("Заявка не найдена: " + approvalId)

        db.approvals().upsert(
            existing.copy(
                status = status,
                resolvedAt = System.currentTimeMillis()
            )
        )

        if (status == "APPROVED") {
            val call = ToolCall(
                id = existing.id,
                name = existing.toolName,
                arguments = JSONObject(existing.arguments)
            )

            val result = if (existing.toolName.startsWith("mcp_")) {
                mcp.executeApproved(call)
            } else if (existing.toolName == "request_external_action") {
                tools.executeApproved(call)
            } else {
                ToolExecution("Approved tool is not executable: " + existing.toolName)
            }

            log(
                conversationId,
                "human",
                "tool_approval.executed",
                result.output.take(500)
            )
            saveAssistant(conversationId, "tool", result.output)
            return result.output
        }

        log(conversationId, "human", "tool_approval." + status.lowercase(), approvalId)
        val answer = if (status == "APPROVED") {
            "Заявка " + approvalId + " одобрена."
        } else {
            "Заявка " + approvalId + " отклонена."
        }
        saveAssistant(conversationId, "system", answer)
        return answer
    }

    suspend fun stream(
        conversationId: String,
        input: String,
        agentId: String = "coordinator",
        onDelta: suspend (String) -> Unit
    ): Result<String> {
        if (input.isBlank()) return Result.failure(IllegalArgumentException("Пустое сообщение."))

        db.messages().insert(
            MessageEntity(UUID.randomUUID().toString(), conversationId, "human", "human", input)
        )
        log(conversationId, "human", "message.sent.streaming", input.take(200))

        return try {
            val history = db.messages().recent(conversationId, 20).reversed()
            val turns = history.map {
                ChatTurn(
                    if (it.senderType == "human") "user" else "assistant",
                    it.content
                )
            }.toMutableList()
            turns.removeAll { it.role == "user" && it.content == input }
            turns += ChatTurn("user", input)

            val answer = provider.streamText(
                agents()[agentId] ?: DefaultAgents.coordinator,
                turns,
                onDelta
            )
            saveAssistant(conversationId, agentId, answer)
            log(conversationId, agentId, "agent.completed.streaming", "chars=" + answer.length)
            Result.success(answer)
        } catch (e: Throwable) {
            val message = "Ошибка streaming: " + (e.message ?: "неизвестная ошибка")
            saveAssistant(conversationId, "system", message)
            Result.failure(e)
        }
    }

    suspend fun runScheduledTask(conversationId: String, prompt: String): Result<String> =
        send(conversationId, "[SCHEDULED TASK] " + prompt, "coordinator")

    private suspend fun saveAssistant(conversationId: String, senderId: String, text: String) {
        db.messages().insert(
            MessageEntity(
                UUID.randomUUID().toString(),
                conversationId,
                "assistant",
                senderId,
                text
            )
        )
        val old = db.conversations().get(conversationId)
        if (old != null) {
            db.conversations().upsert(
                old.copy(updatedAt = System.currentTimeMillis(), unreadCount = old.unreadCount + 1)
            )
        }
    }

    private suspend fun log(
        conversationId: String?,
        actor: String,
        action: String,
        details: String
    ) {
        db.activity().insert(
            ActivityLogEntity(
                UUID.randomUUID().toString(),
                conversationId,
                actor,
                action,
                details.take(1000)
            )
        )
    }

    private fun sanitizeParticipants(
        csv: String,
        availableAgentIds: Set<String>
    ): String =
        csv.split(",")
            .map { it.trim() }
            .filter { availableAgentIds.contains(it) }
            .distinct()
            .ifEmpty { listOf("coordinator") }
            .joinToString(",")
}
