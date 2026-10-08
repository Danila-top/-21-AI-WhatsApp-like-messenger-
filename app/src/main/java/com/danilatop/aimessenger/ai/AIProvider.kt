package com.danilatop.aimessenger.ai

import com.danilatop.aimessenger.security.SecureStore
import com.danilatop.aimessenger.tools.ToolCall
import com.danilatop.aimessenger.tools.ToolDefinition
import com.danilatop.aimessenger.tools.ToolExecution
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

enum class ProviderKind { OPENAI, DEEPSEEK, ANTHROPIC, GEMINI, OPENAI_COMPATIBLE }

data class AgentSpec(
    val id: String,
    val name: String,
    val provider: ProviderKind,
    val model: String,
    val baseUrl: String,
    val keyName: String,
    val systemPrompt: String
)

object DefaultAgents {
    val coordinator = AgentSpec(
        "coordinator", "Luna", ProviderKind.OPENAI, "gpt-5.6-terra",
        "https://api.openai.com", "openai_api_key",
        "You are the coordinator of an AI-first messenger. Be precise, explicit about uncertainty, and delegate complex work when useful."
    )

    val deepseek = AgentSpec(
        "deepseek", "DeepSeek", ProviderKind.DEEPSEEK, "deepseek-flash",
        "https://api.deepseek.com", "deepseek_api_key",
        "You are the engineering and reasoning specialist. Prefer concrete technical solutions and verifiable steps."
    )

    val claude = AgentSpec(
        "claude", "Claude", ProviderKind.ANTHROPIC, "claude-sonnet-4-6",
        "https://api.anthropic.com", "anthropic_api_key",
        "You are the critic and synthesis specialist. Challenge assumptions and improve quality without being vague."
    )

    val gemini = AgentSpec(
        "gemini", "Gemini", ProviderKind.GEMINI, "gemini-3.8-flash",
        "https://generativelanguage.googleapis.com", "gemini_api_key",
        "You are a research and multimodal specialist. Structure evidence and distinguish facts from hypotheses."
    )

    val all = listOf(coordinator, deepseek, claude, gemini)
}

data class ChatTurn(val role: String, val content: String)

class AIProvider(private val secureStore: SecureStore) {
    private val client = OkHttpClient.Builder().build()
    private val jsonType = "application/json".toMediaType()

    suspend fun generate(agent: AgentSpec, turns: List<ChatTurn>): String =
        withContext(Dispatchers.IO) {
            val key = apiKey(agent)
            when (agent.provider) {
                ProviderKind.GEMINI -> geminiText(agent, key, turns)
                ProviderKind.ANTHROPIC -> anthropicText(agent, key, turns)
                else -> openAiStyleText(agent, key, turns)
            }
        }

    suspend fun generateWithTools(
        agent: AgentSpec,
        turns: List<ChatTurn>,
        tools: List<ToolDefinition>,
        execute: suspend (ToolCall) -> ToolExecution,
        maxRounds: Int = 8
    ): String = withContext(Dispatchers.IO) {
        val key = apiKey(agent)
        when (agent.provider) {
            ProviderKind.OPENAI -> openAiResponsesTools(agent, key, turns, tools, execute, maxRounds)
            ProviderKind.DEEPSEEK, ProviderKind.OPENAI_COMPATIBLE ->
                chatCompletionsTools(agent, key, turns, tools, execute, maxRounds)
            ProviderKind.ANTHROPIC -> anthropicTools(agent, key, turns, tools, execute, maxRounds)
            ProviderKind.GEMINI -> geminiInteractionTools(agent, key, turns, tools, execute, maxRounds)
        }
    }

    private fun apiKey(agent: AgentSpec): String =
        secureStore.get(agent.keyName)
            ?: error("Нет API-ключа для " + agent.name + ". Открой Настройки → Провайдеры.")

    private fun openAiResponsesTools(
        agent: AgentSpec,
        key: String,
        turns: List<ChatTurn>,
        tools: List<ToolDefinition>,
        execute: suspend (ToolCall) -> ToolExecution,
        maxRounds: Int
    ): String {
        var response = postJson(
            agent.baseUrl.trimEnd('/') + "/v1/responses",
            "Bearer " + key,
            JSONObject()
                .put("model", agent.model)
                .put("instructions", agent.systemPrompt)
                .put("input", turns.toJsonInput())
                .put("tools", tools.toResponsesJson())
                .put("store", false)
                .toString()
        )

        for (round in 0 until maxRounds) {
            val output = response.optJSONArray("output") ?: JSONArray()
            val calls = parseOpenAiResponsesCalls(output)
            if (calls.isEmpty()) return extractOpenAiOutputText(output)

            val nextInput = JSONArray()
            for (i in 0 until output.length()) {
                nextInput.put(output.get(i))
            }
            for (call in calls) {
                val result = runTool(call, execute)
                nextInput.put(
                    JSONObject()
                        .put("type", "function_call_output")
                        .put("call_id", call.id)
                        .put("output", result.output)
                )
            }

            response = postJson(
                agent.baseUrl.trimEnd('/') + "/v1/responses",
                "Bearer " + key,
                JSONObject()
                    .put("model", agent.model)
                    .put("instructions", agent.systemPrompt)
                    .put("input", nextInput)
                    .put("tools", tools.toResponsesJson())
                    .put("store", false)
                    .toString()
            )
        }

        error("Превышено число раундов tool-calling.")
    }

    private fun chatCompletionsTools(
        agent: AgentSpec,
        key: String,
        turns: List<ChatTurn>,
        tools: List<ToolDefinition>,
        execute: suspend (ToolCall) -> ToolExecution,
        maxRounds: Int
    ): String {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", agent.systemPrompt))
        turns.forEach { messages.put(JSONObject().put("role", it.role).put("content", it.content)) }

        for (round in 0 until maxRounds) {
            val response = postJson(
                agent.baseUrl.trimEnd('/') + "/v1/chat/completions",
                "Bearer " + key,
                JSONObject()
                    .put("model", agent.model)
                    .put("messages", messages)
                    .put("tools", tools.toOpenAIChatJson())
                    .put("tool_choice", "auto")
                    .put("stream", false)
                    .toString()
            )

            val message = response
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")

            messages.put(message)

            val calls = message.optJSONArray("tool_calls")
            if (calls == null || calls.length() == 0) {
                return message.optString("content")
            }

            for (i in 0 until calls.length()) {
                val raw = calls.getJSONObject(i)
                val fn = raw.getJSONObject("function")
                val call = ToolCall(
                    id = raw.getString("id"),
                    name = fn.getString("name"),
                    arguments = JSONObject(fn.getString("arguments"))
                )
                val result = runTool(call, execute)
                messages.put(
                    JSONObject()
                        .put("role", "tool")
                        .put("tool_call_id", call.id)
                        .put("content", result.output)
                )
            }
        }

        error("Превышено число раундов tool-calling.")
    }

    private fun anthropicTools(
        agent: AgentSpec,
        key: String,
        turns: List<ChatTurn>,
        tools: List<ToolDefinition>,
        execute: suspend (ToolCall) -> ToolExecution,
        maxRounds: Int
    ): String {
        val messages = JSONArray()
        turns.forEach {
            messages.put(
                JSONObject()
                    .put("role", if (it.role == "assistant") "assistant" else "user")
                    .put("content", it.content)
            )
        }

        for (round in 0 until maxRounds) {
            val response = postJson(
                agent.baseUrl.trimEnd('/') + "/v1/messages",
                null,
                JSONObject()
                    .put("model", agent.model)
                    .put("max_tokens", 4096)
                    .put("system", agent.systemPrompt)
                    .put("messages", messages)
                    .put("tools", tools.toAnthropicJson())
                    .toString(),
                mapOf(
                    "x-api-key" to key,
                    "anthropic-version" to "2023-06-01"
                )
            )

            val content = response.getJSONArray("content")
            val calls = parseAnthropicCalls(content)
            if (calls.isEmpty()) return extractAnthropicText(content)

            messages.put(JSONObject().put("role", "assistant").put("content", content))
            val results = JSONArray()
            for (call in calls) {
                val result = runTool(call, execute)
                results.put(
                    JSONObject()
                        .put("type", "tool_result")
                        .put("tool_use_id", call.id)
                        .put("content", result.output)
                )
            }
            messages.put(JSONObject().put("role", "user").put("content", results))
        }

        error("Превышено число раундов tool-calling.")
    }

    private fun geminiInteractionTools(
        agent: AgentSpec,
        key: String,
        turns: List<ChatTurn>,
        tools: List<ToolDefinition>,
        execute: suspend (ToolCall) -> ToolExecution,
        maxRounds: Int
    ): String {
        var input: Any = turns.joinToString("\n") { it.role + ": " + it.content }
        var previousId: String? = null

        for (round in 0 until maxRounds) {
            val body = JSONObject()
                .put("model", agent.model)
                .put("input", input)
                .put("tools", tools.toGeminiInteractionJson())

            if (previousId != null) body.put("previous_interaction_id", previousId)

            val response = postJson(
                agent.baseUrl.trimEnd('/') + "/v1beta/interactions",
                key,
                body.toString(),
                mapOf("x-goog-api-key" to key)
            )

            previousId = response.getString("id")
            val steps = response.optJSONArray("steps") ?: JSONArray()
            val calls = parseGeminiInteractionCalls(steps)
            if (calls.isEmpty()) return extractGeminiInteractionText(steps)

            val results = JSONArray()
            for (call in calls) {
                val result = runTool(call, execute)
                results.put(
                    JSONObject()
                        .put("type", "function_result")
                        .put("name", call.name)
                        .put("call_id", call.id)
                        .put(
                            "result",
                            JSONArray().put(
                                JSONObject()
                                    .put("type", "text")
                                    .put("text", result.output)
                            )
                        )
                )
            }
            input = results
        }

        error("Превышено число раундов tool-calling.")
    }

    private suspend fun runTool(
        call: ToolCall,
        execute: suspend (ToolCall) -> ToolExecution
    ): ToolExecution = try {
        execute(call)
    } catch (e: Throwable) {
        ToolExecution("Tool execution failed: " + (e.message ?: "unknown error"))
    }

    private fun parseOpenAiResponsesCalls(output: JSONArray): List<ToolCall> {
        val calls = mutableListOf<ToolCall>()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            if (item.optString("type") != "function_call") continue
            val args = runCatching { JSONObject(item.optString("arguments", "{}")) }.getOrDefault(JSONObject())
            calls += ToolCall(
                item.optString("call_id", item.optString("id")),
                item.getString("name"),
                args
            )
        }
        return calls
    }

    private fun parseAnthropicCalls(content: JSONArray): List<ToolCall> {
        val calls = mutableListOf<ToolCall>()
        for (i in 0 until content.length()) {
            val item = content.optJSONObject(i) ?: continue
            if (item.optString("type") != "tool_use") continue
            calls += ToolCall(
                item.getString("id"),
                item.getString("name"),
                item.optJSONObject("input") ?: JSONObject()
            )
        }
        return calls
    }

    private fun parseGeminiInteractionCalls(steps: JSONArray): List<ToolCall> {
        val calls = mutableListOf<ToolCall>()
        for (i in 0 until steps.length()) {
            val item = steps.optJSONObject(i) ?: continue
            if (item.optString("type") != "function_call") continue
            calls += ToolCall(
                item.optString("id"),
                item.getString("name"),
                item.optJSONObject("arguments") ?: JSONObject()
            )
        }
        return calls
    }

    private fun extractOpenAiOutputText(output: JSONArray): String {
        val result = StringBuilder()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            if (item.optString("type") != "message") continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.optJSONObject(j) ?: continue
                if (part.optString("type") == "output_text") result.append(part.optString("text"))
            }
        }
        require(result.isNotBlank()) { "OpenAI вернул пустой ответ." }
        return result.toString()
    }

    private fun extractAnthropicText(content: JSONArray): String {
        val result = StringBuilder()
        for (i in 0 until content.length()) {
            val item = content.optJSONObject(i) ?: continue
            if (item.optString("type") == "text") result.append(item.optString("text"))
        }
        require(result.isNotBlank()) { "Anthropic вернул пустой текстовый ответ." }
        return result.toString()
    }

    private fun extractGeminiInteractionText(steps: JSONArray): String {
        val result = StringBuilder()
        for (i in 0 until steps.length()) {
            val item = steps.optJSONObject(i) ?: continue
            if (item.optString("type") == "text") result.append(item.optString("text"))
            if (item.optString("type") == "message") result.append(item.optString("text"))
        }
        require(result.isNotBlank()) { "Gemini вернул пустой текстовый ответ." }
        return result.toString()
    }

    private fun openAiStyleText(agent: AgentSpec, key: String, turns: List<ChatTurn>): String {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", agent.systemPrompt))
        turns.forEach { messages.put(JSONObject().put("role", it.role).put("content", it.content)) }

        val response = postJson(
            agent.baseUrl.trimEnd('/') + "/v1/chat/completions",
            "Bearer " + key,
            JSONObject()
                .put("model", agent.model)
                .put("messages", messages)
                .put("stream", false)
                .toString()
        )

        return response
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .optString("content")
    }

    private fun anthropicText(agent: AgentSpec, key: String, turns: List<ChatTurn>): String {
        val messages = JSONArray()
        turns.forEach {
            messages.put(
                JSONObject()
                    .put("role", if (it.role == "assistant") "assistant" else "user")
                    .put("content", it.content)
            )
        }

        val response = postJson(
            agent.baseUrl.trimEnd('/') + "/v1/messages",
            null,
            JSONObject()
                .put("model", agent.model)
                .put("max_tokens", 4096)
                .put("system", agent.systemPrompt)
                .put("messages", messages)
                .toString(),
            mapOf(
                "x-api-key" to key,
                "anthropic-version" to "2023-06-01"
            )
        )

        return response.getJSONArray("content")
            .getJSONObject(0)
            .getString("text")
    }

    private fun geminiText(agent: AgentSpec, key: String, turns: List<ChatTurn>): String {
        val input = turns.joinToString("\n") { it.role + ": " + it.content }
        val response = postJson(
            agent.baseUrl.trimEnd('/') + "/v1beta/interactions",
            null,
            JSONObject()
                .put("model", agent.model)
                .put("input", agent.systemPrompt + "\n\n" + input)
                .toString(),
            mapOf("x-goog-api-key" to key)
        )
        val steps = response.optJSONArray("steps") ?: JSONArray()
        return extractGeminiInteractionText(steps)
    }

    private fun postJson(
        url: String,
        bearer: String?,
        body: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): JSONObject {
        val builder = Request.Builder()
            .url(url)
            .post(body.toRequestBody(jsonType))

        if (bearer != null) builder.addHeader("Authorization", bearer)
        extraHeaders.forEach { (name, value) -> builder.addHeader(name, value) }

        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP " + response.code + ": " + text)
            return JSONObject(text)
        }
    }
}

private fun List<ChatTurn>.toJsonInput(): JSONArray = JSONArray().apply {
    forEach {
        put(
            JSONObject()
                .put("role", it.role)
                .put("content", it.content)
        )
    }
}

private fun List<ToolDefinition>.toResponsesJson(): JSONArray = JSONArray().apply {
    forEach {
        put(
            JSONObject()
                .put("type", "function")
                .put("name", it.name)
                .put("description", it.description)
                .put("parameters", it.parameters)
                .put("strict", true)
        )
    }
}

private fun List<ToolDefinition>.toOpenAIChatJson(): JSONArray = JSONArray().apply {
    forEach {
        put(
            JSONObject()
                .put("type", "function")
                .put(
                    "function",
                    JSONObject()
                        .put("name", it.name)
                        .put("description", it.description)
                        .put("parameters", it.parameters)
                        .put("strict", true)
                )
        )
    }
}

private fun List<ToolDefinition>.toAnthropicJson(): JSONArray = JSONArray().apply {
    forEach {
        put(
            JSONObject()
                .put("name", it.name)
                .put("description", it.description)
                .put("input_schema", it.parameters)
        )
    }
}

private fun List<ToolDefinition>.toGeminiInteractionJson(): JSONArray = JSONArray().apply {
    forEach {
        put(
            JSONObject()
                .put("type", "function")
                .put("name", it.name)
                .put("description", it.description)
                .put("parameters", it.parameters)
        )
    }
}
