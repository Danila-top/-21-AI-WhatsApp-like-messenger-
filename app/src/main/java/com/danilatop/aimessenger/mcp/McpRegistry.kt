package com.danilatop.aimessenger.mcp

import com.danilatop.aimessenger.data.AppDatabase
import com.danilatop.aimessenger.data.ToolApprovalEntity
import com.danilatop.aimessenger.security.SecureStore
import com.danilatop.aimessenger.tools.ToolCall
import com.danilatop.aimessenger.tools.ToolDefinition
import com.danilatop.aimessenger.tools.ToolExecution
import com.danilatop.aimessenger.tools.ToolRisk

class McpRegistry(
    private val db: AppDatabase,
    private val secureStore: SecureStore
) {
    private val clients = mutableMapOf<String, McpClient>()

    private suspend fun client(serverId: String): McpClient {
        clients[serverId]?.let { return it }

        val server = db.mcpServers().get(serverId)
            ?: error("MCP server not found: " + serverId)

        val token = server.tokenKeyName?.let { secureStore.get(it) }
        val headers = if (token.isNullOrBlank()) {
            emptyMap()
        } else {
            mapOf("Authorization" to "Bearer " + token)
        }

        val created = McpClient(server.endpoint, headers)
        created.initialize()
        clients[serverId] = created
        return created
    }

    suspend fun definitions(): List<ToolDefinition> {
        val result = mutableListOf<ToolDefinition>()

        for (server in db.mcpServers().enabled()) {
            runCatching {
                val tools = client(server.id).listTools()
                tools.forEach { tool ->
                    result += ToolDefinition(
                        name = mcpName(server.id, tool.name),
                        description = server.name + " / " + tool.description,
                        parameters = tool.inputSchema,
                        risk = ToolRisk.CONFIRM
                    )
                }
            }
        }

        return result
    }

    suspend fun execute(
        conversationId: String,
        call: ToolCall
    ): ToolExecution {
        require(call.name.startsWith("mcp_")) { "Not an MCP tool." }

        val encoded = call.name.removePrefix("mcp_")
        val split = encoded.split("__", limit = 2)
        require(split.size == 2) { "Invalid MCP tool name." }

        val serverId = split[0]
        val toolName = split[1]
        val server = db.mcpServers().get(serverId)
            ?: return ToolExecution("MCP server not found: " + serverId)

        val approvalId = "mcp-" + java.util.UUID.randomUUID().toString()

        db.approvals().upsert(
            ToolApprovalEntity(
                id = approvalId,
                conversationId = conversationId,
                toolName = call.name,
                arguments = call.arguments.toString(),
                status = "PENDING"
            )
        )

        return ToolExecution(
            output = "Human approval required before MCP call. approval_id=" + approvalId +
                " server=" + server.name + " tool=" + toolName,
            requiresApproval = true,
            approvalId = approvalId
        )
    }

    suspend fun executeApproved(call: ToolCall): ToolExecution {
        val encoded = call.name.removePrefix("mcp_")
        val split = encoded.split("__", limit = 2)
        require(split.size == 2) { "Invalid MCP tool name." }

        val serverId = split[0]
        val toolName = split[1]
        val result = client(serverId).callTool(toolName, call.arguments)

        return ToolExecution(
            output = result.text,
            requiresApproval = false
        )
    }

    private fun mcpName(serverId: String, toolName: String): String =
        "mcp_" + serverId + "__" + toolName
}
