package com.danilatop.aimessenger.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: JSONObject
)

data class McpCallResult(
    val text: String,
    val isError: Boolean = false
)

class McpClient(
    private val endpoint: String,
    private val headers: Map<String, String> = emptyMap(),
    private val client: OkHttpClient = OkHttpClient.Builder().build()
) {
    private val mediaType = "application/json".toMediaType()
    private var sessionId: String? = null
    private var nextId = 1

    suspend fun initialize(): JSONObject = withContext(Dispatchers.IO) {
        val result = request(
            "initialize",
            JSONObject()
                .put("protocolVersion", "2025-11-25")
                .put(
                    "capabilities",
                    JSONObject()
                        .put("tools", JSONObject())
                )
                .put(
                    "clientInfo",
                    JSONObject()
                        .put("name", "ai-messenger-21")
                        .put("version", "0.1.0")
                )
        )
        sessionId = result.second
        notifyInitialized()
        result.first.getJSONObject("result")
    }

    suspend fun listTools(): List<McpTool> = withContext(Dispatchers.IO) {
        val response = request("tools/list", JSONObject())
        sessionId = response.second ?: sessionId
        val tools = response.first
            .getJSONObject("result")
            .optJSONArray("tools")
            ?: JSONArray()

        buildList {
            for (i in 0 until tools.length()) {
                val item = tools.getJSONObject(i)
                add(
                    McpTool(
                        name = item.getString("name"),
                        description = item.optString("description"),
                        inputSchema = item.optJSONObject("inputSchema") ?: JSONObject()
                    )
                )
            }
        }
    }

    suspend fun callTool(name: String, arguments: JSONObject): McpCallResult =
        withContext(Dispatchers.IO) {
            val response = request(
                "tools/call",
                JSONObject()
                    .put("name", name)
                    .put("arguments", arguments)
            )
            sessionId = response.second ?: sessionId

            val result = response.first.optJSONObject("result") ?: JSONObject()
            val content = result.optJSONArray("content") ?: JSONArray()
            val text = buildString {
                for (i in 0 until content.length()) {
                    val item = content.optJSONObject(i) ?: continue
                    if (item.optString("type") == "text") {
                        append(item.optString("text"))
                    }
                }
            }

            McpCallResult(
                text = if (text.isBlank()) result.toString() else text,
                isError = result.optBoolean("isError", false)
            )
        }

    private fun notifyInitialized() {
        val rpc = JSONObject()
            .put("jsonrpc", "2.0")
            .put("method", "notifications/initialized")
            .put("params", JSONObject())

        val builder = Request.Builder()
            .url(endpoint)
            .addHeader("Accept", "application/json, text/event-stream")
            .post(rpc.toString().toRequestBody(mediaType))

        headers.forEach { (name, value) -> builder.addHeader(name, value) }
        sessionId?.let { builder.addHeader("MCP-Session-Id", it) }
        builder.addHeader("MCP-Protocol-Version", "2025-11-25")
        builder.addHeader("Mcp-Method", "notifications/initialized")

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful && response.code != 202) {
                error(
                    "MCP initialized notification failed: " +
                        response.code + ": " + response.body?.string().orEmpty()
                )
            }
        }
    }

    private fun request(method: String, params: JSONObject): Pair<JSONObject, String?> {
        val requestId = nextId++
        val rpc = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", requestId)
            .put("method", method)
            .put("params", params)

        val builder = Request.Builder()
            .url(endpoint)
            .addHeader("Accept", "application/json, text/event-stream")
            .post(rpc.toString().toRequestBody(mediaType))

        headers.forEach { (name, value) -> builder.addHeader(name, value) }

        sessionId?.let { builder.addHeader("MCP-Session-Id", it) }
        builder.addHeader("MCP-Protocol-Version", "2025-11-25")
        builder.addHeader("Mcp-Method", method)
        builder.addHeader("Mcp-Name", if (method == "tools/call") params.optString("name") else "ai-messenger-21")
        builder.addHeader("X-Request-Id", UUID.randomUUID().toString())

        client.newCall(builder.build()).execute().use { response ->
            val responseSession = response.header("Mcp-Session-Id")
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("MCP HTTP " + response.code + ": " + body)
            }

            val json = parseJsonOrEventStream(body)
            if (json.has("error")) {
                error("MCP error: " + json.getJSONObject("error").toString())
            }
            return json to responseSession
        }
    }

    private fun parseJsonOrEventStream(body: String): JSONObject {
        val trimmed = body.trim()
        if (trimmed.startsWith("{")) return JSONObject(trimmed)

        val dataLines = trimmed
            .lineSequence()
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .filter { it.isNotBlank() }

        val joined = dataLines.firstOrNull()
            ?: error("MCP returned an empty response.")
        return JSONObject(joined)
    }
}
