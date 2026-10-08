package com.danilatop.aimessenger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danilatop.aimessenger.ai.AIProvider
import com.danilatop.aimessenger.agent.AgentRuntime
import com.danilatop.aimessenger.data.AppDatabase
import com.danilatop.aimessenger.data.MemoryEntity
import com.danilatop.aimessenger.security.SecureStore
import com.danilatop.aimessenger.data.McpServerEntity
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AIMessengerViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val store = SecureStore(app)
    private val runtime = AgentRuntime(app, db, AIProvider(store))
    private val _streamingText = MutableStateFlow("")
    val streamingText = _streamingText.asStateFlow()

    val conversations = db.conversations().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val memories = db.memories().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activity = db.activity().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val mcpServers = db.mcpServers().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun messages(conversationId: String) =
        db.messages().observeForConversation(conversationId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun ensureSeed() {
        viewModelScope.launch {
            if (conversations.value.isEmpty()) {
                runtime.createConversation("Лаборатория AI", "coordinator,deepseek,claude,gemini")
            }
        }
    }

    fun newChat(title: String, participants: String = "coordinator") {
        viewModelScope.launch { runtime.createConversation(title, participants) }
    }

    fun sendStreaming(conversationId: String, text: String, agentId: String = "coordinator") {
        if (text.isBlank()) return
        viewModelScope.launch {
            _streamingText.value = ""
            runtime.stream(conversationId, text.trim(), agentId) { delta ->
                _streamingText.value += delta
            }
            _streamingText.value = ""
        }
    }

    fun send(conversationId: String, text: String, agentId: String = "coordinator") {
        if (text.isBlank()) return
        viewModelScope.launch { runtime.send(conversationId, text.trim(), agentId) }
    }

    fun setAutonomous(conversationId: String, enabled: Boolean) {
        viewModelScope.launch {
            val current = db.conversations().get(conversationId) ?: return@launch
            db.conversations().setAutonomous(
                conversationId,
                enabled,
                System.currentTimeMillis()
            )
        }
    }

    fun connectMcpServer(name: String, endpoint: String, token: String) {
        if (name.isBlank() || endpoint.isBlank()) return
        viewModelScope.launch {
            val id = UUID.nameUUIDFromBytes(endpoint.trim().toByteArray()).toString()
            val tokenKey = "mcp_token_" + id
            if (token.isNotBlank()) store.put(tokenKey, token.trim())
            db.mcpServers().upsert(
                McpServerEntity(
                    id = id,
                    name = name.trim(),
                    endpoint = endpoint.trim(),
                    tokenKeyName = if (token.isBlank()) null else tokenKey,
                    enabled = true
                )
            )
        }
    }

    fun setMcpServerEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            db.mcpServers().setEnabled(id, enabled)
        }
    }

    fun setApiKey(provider: String, value: String) {
        val key = when (provider) {
            "OpenAI" -> "openai_api_key"
            "DeepSeek" -> "deepseek_api_key"
            "Claude" -> "anthropic_api_key"
            "Gemini" -> "gemini_api_key"
            else -> return
        }
        if (value.isNotBlank()) store.put(key, value.trim())
    }

    fun addMemory(key: String, value: String) {
        if (key.isBlank() || value.isBlank()) return
        viewModelScope.launch {
            db.memories().upsert(
                MemoryEntity(
                    id = key.trim().lowercase(),
                    scope = "global",
                    key = key.trim(),
                    value = value.trim(),
                    importance = 70
                )
            )
        }
    }

    fun conversationById(id: String) = conversations.value.firstOrNull { it.id == id }
}
