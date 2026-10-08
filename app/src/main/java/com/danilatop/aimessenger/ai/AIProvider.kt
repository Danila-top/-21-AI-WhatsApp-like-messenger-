package com.danilatop.aimessenger.ai

import com.danilatop.aimessenger.security.SecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

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
        id = "coordinator",
        name = "Luna",
        provider = ProviderKind.OPENAI,
        model = "gpt-5.6",
        baseUrl = "https://api.openai.com",
        keyName = "openai_api_key",
        systemPrompt = "You are the coordinator of an AI-first messenger. Be precise, explicit about uncertainty, and delegate complex work when useful."
    )

    val deepseek = AgentSpec(
        id = "deepseek",
        name = "DeepSeek",
        provider = ProviderKind.DEEPSEEK,
        model = "deepseek-chat",
        baseUrl = "https://api.deepseek.com",
        keyName = "deepseek_api_key",
        systemPrompt = "You are the engineering and reasoning specialist. Prefer concrete technical solutions and verifiable steps."
    )

    val claude = AgentSpec(
        id = "claude",
        name = "Claude",
        provider = ProviderKind.ANTHROPIC,
        model = "claude-sonnet-4-5",
        baseUrl = "https://api.anthropic.com",
        keyName = "anthropic_api_key",
        systemPrompt = "You are the critic and synthesis specialist. Challenge assumptions and improve quality without being vague."
    )

    val gemini = AgentSpec(
        id = "gemini",
        name = "Gemini",
        provider = ProviderKind.GEMINI,
        model = "gemini-2.5-flash",
        baseUrl = "https://generativelanguage.googleapis.com",
        keyName = "gemini_api_key",
        systemPrompt = "You are a research and multimodal specialist. Structure evidence and distinguish facts from hypotheses."
    )

    val all = listOf(coordinator, deepseek, claude, gemini)
}

data class ChatTurn(val role: String, val content: String)

class AIProvider(private val secureStore: SecureStore) {
    private val client = OkHttpClient.Builder().build()
    private val jsonType = "application/json".toMediaType()

    suspend fun generate(agent: AgentSpec, turns: List<ChatTurn>): String =
        withContext(Dispatchers.IO) {
            val key = secureStore.get(agent.keyName)
                ?: error("Нет API-ключа для " + agent.name + ". Открой Настройки → Провайдеры.")

            when (agent.provider) {
                ProviderKind.OPENAI,
                ProviderKind.DEEPSEEK,
                ProviderKind.OPENAI_COMPATIBLE -> openAiStyle(agent, key, turns)
                ProviderKind.ANTHROPIC -> anthropic(agent, key, turns)
                ProviderKind.GEMINI -> gemini(agent, key, turns)
            }
        }

    private fun openAiStyle(agent: AgentSpec, key: String, turns: List<ChatTurn>): String {
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", agent.systemPrompt))
            turns.forEach {
                put(JSONObject().put("role", it.role).put("content", it.content))
            }
        }

        val body = JSONObject()
            .put("model", agent.model)
            .put("messages", messages)
            .put("temperature", 0.2)
            .put("stream", false)
            .toString()

        val base = agent.baseUrl.trimEnd('/')
        val url = if (base.endsWith("/v1")) {
            base + "/chat/completions"
        } else {
            base + "/v1/chat/completions"
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + key)
            .post(body.toRequestBody(jsonType))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("HTTP " + response.code + ": " + text)
            }
            return JSONObject(text)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }

    private fun anthropic(agent: AgentSpec, key: String, turns: List<ChatTurn>): String {
        val messages = JSONArray()
        turns.forEach {
            if (it.role != "system") {
                messages.put(
                    JSONObject()
                        .put("role", if (it.role == "assistant") "assistant" else "user")
                        .put("content", it.content)
                )
            }
        }

        val body = JSONObject()
            .put("model", agent.model)
            .put("max_tokens", 4096)
            .put("system", agent.systemPrompt)
            .put("messages", messages)
            .toString()

        val url = agent.baseUrl.trimEnd('/') + "/v1/messages"
        val request = Request.Builder()
            .url(url)
            .addHeader("x-api-key", key)
            .addHeader("anthropic-version", "2023-06-01")
            .post(body.toRequestBody(jsonType))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("HTTP " + response.code + ": " + text)
            }
            return JSONObject(text)
                .getJSONArray("content")
                .getJSONObject(0)
                .getString("text")
        }
    }

    private fun gemini(agent: AgentSpec, key: String, turns: List<ChatTurn>): String {
        val prompt = buildString {
            append(agent.systemPrompt)
            append("\n\n")
            turns.forEach {
                append(it.role)
                append(": ")
                append(it.content)
                append("\n")
            }
        }

        val contents = JSONArray().put(
            JSONObject()
                .put("role", "user")
                .put(
                    "parts",
                    JSONArray().put(
                        JSONObject().put("text", prompt)
                    )
                )
        )

        val url = agent.baseUrl.trimEnd('/') +
            "/v1beta/models/" + agent.model +
            ":generateContent?key=" + key

        val body = JSONObject()
            .put("contents", contents)
            .toString()

        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(jsonType))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("HTTP " + response.code + ": " + text)
            }
            return JSONObject(text)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        }
    }
}
