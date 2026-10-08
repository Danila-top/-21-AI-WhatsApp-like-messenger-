package com.danilatop.aimessenger.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class AiStreamingClient(
    private val client: OkHttpClient = OkHttpClient.Builder().build()
) {
    private val mediaType = "application/json".toMediaType()

    suspend fun streamOpenAiCompatible(
        agent: AgentSpec,
        apiKey: String,
        turns: List<ChatTurn>,
        onDelta: suspend (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", agent.systemPrompt))

        turns.forEach {
            messages.put(
                JSONObject()
                    .put("role", it.role)
                    .put("content", it.content)
            )
        }

        val body = JSONObject()
            .put("model", agent.model)
            .put("messages", messages)
            .put("stream", true)
            .toString()

        val request = Request.Builder()
            .url(agent.baseUrl.trimEnd('/') + "/v1/chat/completions")
            .addHeader("Authorization", "Bearer " + apiKey)
            .addHeader("Accept", "text/event-stream")
            .post(body.toRequestBody(mediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP " + response.code + ": " + (response.body?.string().orEmpty()))
            }

            val source = response.body?.source() ?: error("Empty streaming response body.")
            val answer = StringBuilder()

            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue

                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                if (data.isBlank()) continue

                val json = runCatching { JSONObject(data) }.getOrNull() ?: continue
                val choices = json.optJSONArray("choices") ?: JSONArray()
                for (i in 0 until choices.length()) {
                    val delta = choices.optJSONObject(i)
                        ?.optJSONObject("delta")
                        ?.optString("content")
                        .orEmpty()
                    if (delta.isNotEmpty()) {
                        answer.append(delta)
                        onDelta(delta)
                    }
                }
            }

            answer.toString()
        }
    }
}
