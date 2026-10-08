package com.danilatop.aimessenger.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val participantAgentIds: String = "coordinator",
    val pinned: Boolean = false,
    val autonomous: Boolean = false,
    val archived: Boolean = false,
    val unreadCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderType: String,
    val senderId: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val toolName: String? = null,
    val toolStatus: String? = null
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val scope: String,
    val key: String,
    val value: String,
    val importance: Int = 50,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "activity_log")
data class ActivityLogEntity(
    @PrimaryKey val id: String,
    val conversationId: String?,
    val actor: String,
    val action: String,
    val details: String,
    val createdAt: Long = System.currentTimeMillis()
)
