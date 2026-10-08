package com.danilatop.aimessenger.agent

import com.danilatop.aimessenger.ai.AIProvider
import com.danilatop.aimessenger.ai.AgentSpec
import com.danilatop.aimessenger.ai.ChatTurn
import com.danilatop.aimessenger.ai.DefaultAgents
import com.danilatop.aimessenger.data.ActivityLogEntity
import com.danilatop.aimessenger.data.AppDatabase
import com.danilatop.aimessenger.data.ConversationEntity
import com.danilatop.aimessenger.data.MemoryEntity
import com.danilatop.aimessenger.data.MessageEntity
import com.danilatop.aimessenger.tools.ToolRegistry
import java.util.UUID

class AgentRuntime(
    private val db: AppDatabase,
    private val provider: AIProvider,
    private val tools: ToolRegistry = ToolRegistry()
) {
    private val agents = DefaultAgents.all.associateBy { it.id }

    suspend fun createConversation(title: String = "Новый AI-чат", agentsCsv: String = "coordinator"): String {
        val id = UUID.randomUUID().toString()
        db.conversations().upsert(ConversationEntity(id = id, title = title, participantAgentIds = agentsCsv))
        log(id, "system", "conversation.created", "participants=" + agentsCsv)
        return id
    }

    suspend fun send(conversationId: String, input: String, agentId: String = "coordinator"): Result<String> {
        db.messages().insert(MessageEntity(UUID.randomUUID().toString(), conversationId, "human", "human", input))
        log(conversationId, "human", "message.sent", input.take(200))

        return runCatching {
            when {
                input.startsWith("/calc ") -> {
                    val result = tools.calculate(input.removePrefix("/calc "))
                    saveAssistant(conversationId, "calculator", result)
                    result
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
                input.startsWith("/team ") -> team(conversationId, input.removePrefix("/team "))
                else -> oneAgent(conversationId, input, agents[agentId] ?: DefaultAgents.coordinator)
            }
        }.onFailure {
            saveAssistant(conversationId, "system", "Ошибка агента: " + (it.message ?: "неизвестная ошибка"))
        }
    }

    private suspend fun oneAgent(conversationId: String, input: String, agent: AgentSpec): String {
        log(conversationId, agent.id, "agent.thinking", "model=" + agent.model)
        val history = db.messages().recent(conversationId, 20).reversed()
        val memories = db.memories().recent(12)
        val memoryPrefix = if (memories.isEmpty()) "" else
            "\nLong-term memory:\n" + memories.joinToString("\n") { "- " + it.key + ": " + it.value }

        val turns = history.map {
            ChatTurn(if (it.senderType == "human") "user" else "assistant", it.content)
        }.toMutableList()
        turns.removeAll { it.role == "user" && it.content == input }
        turns += ChatTurn("user", input + memoryPrefix)

        val answer = provider.generate(agent, turns)
        saveAssistant(conversationId, agent.id, answer)
        log(conversationId, agent.id, "agent.completed", "chars=" + answer.length)
        return answer
    }

    private suspend fun team(conversationId: String, input: String): String {
        val requested = listOf(DefaultAgents.deepseek, DefaultAgents.claude, DefaultAgents.gemini)
        val reports = mutableListOf<String>()
        requested.forEach { agent ->
            runCatching { oneAgent(conversationId, input, agent) }
                .onSuccess { reports += "### " + agent.name + "\n" + it }
                .onFailure { reports += "### " + agent.name + "\nFAILED: " + it.message }
        }

        val synthesis = buildString {
            append("You are the lead coordinator. Synthesize independent agent reports into one rigorous answer. ")
            append("Do not hide disagreements. Mark uncertain claims.\n\n")
            append(reports.joinToString("\n\n"))
            append("\n\nOriginal task: ").append(input)
        }
        val final = provider.generate(DefaultAgents.coordinator, listOf(ChatTurn("user", synthesis)))
        saveAssistant(conversationId, DefaultAgents.coordinator.id, final)
        log(conversationId, "coordinator", "team.completed", "agents=" + requested.size)
        return final
    }

    private suspend fun saveAssistant(conversationId: String, senderId: String, text: String) {
        db.messages().insert(MessageEntity(UUID.randomUUID().toString(), conversationId, "assistant", senderId, text))
        val old = db.conversations().get(conversationId)
        if (old != null) db.conversations().upsert(old.copy(updatedAt = System.currentTimeMillis()))
    }

    private suspend fun log(conversationId: String?, actor: String, action: String, details: String) {
        db.activity().insert(ActivityLogEntity(UUID.randomUUID().toString(), conversationId, actor, action, details))
    }
