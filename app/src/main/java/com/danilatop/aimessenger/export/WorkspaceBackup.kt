package com.danilatop.aimessenger.export

import android.content.Context
import com.danilatop.aimessenger.data.AppDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object WorkspaceBackup {
    suspend fun exportTo(context: Context): File {
        val db = AppDatabase.get(context)

        val root = JSONObject()

        val conversations = JSONArray()
        db.conversations().observeAll().collectOnce().forEach { item ->
            conversations.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("participantAgentIds", item.participantAgentIds)
                    .put("pinned", item.pinned)
                    .put("autonomous", item.autonomous)
                    .put("archived", item.archived)
                    .put("unreadCount", item.unreadCount)
                    .put("createdAt", item.createdAt)
                    .put("updatedAt", item.updatedAt)
            )
        }
        root.put("conversations", conversations)

        val memories = JSONArray()
        db.memories().observeAll().collectOnce().forEach { item ->
            memories.put(
                JSONObject()
                    .put("id", item.id)
                    .put("scope", item.scope)
                    .put("key", item.key)
                    .put("value", item.value)
                    .put("importance", item.importance)
                    .put("createdAt", item.createdAt)
                    .put("updatedAt", item.updatedAt)
            )
        }
        root.put("memories", memories)

        val activities = JSONArray()
        db.activity().observeAll().collectOnce().forEach { item ->
            activities.put(
                JSONObject()
                    .put("id", item.id)
                    .put("conversationId", item.conversationId)
                    .put("actor", item.actor)
                    .put("action", item.action)
                    .put("details", item.details)
                    .put("createdAt", item.createdAt)
            )
        }
        root.put("activity", activities)

        val file = File(context.filesDir, "ai-messenger-backup.json")
        file.writeText(root.toString(2))
        return file
    }

    private suspend fun <T> kotlinx.coroutines.flow.Flow<List<T>>.collectOnce(): List<T> {
        var result: List<T> = emptyList()
        kotlinx.coroutines.flow.firstOrNull(this)?.let { result = it }
        return result
    }
}
