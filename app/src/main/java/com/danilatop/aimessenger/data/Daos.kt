package com.danilatop.aimessenger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE archived = 0 ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun get(id: String): ConversationEntity?

    @Query("UPDATE conversations SET autonomous = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setAutonomous(id: String, enabled: Boolean, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ConversationEntity)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(conversationId: String, limit: Int): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MessageEntity)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY importance DESC, updatedAt DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY importance DESC, updatedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MemoryEntity)
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity_log ORDER BY createdAt DESC LIMIT 200")
    fun observeAll(): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_log WHERE conversationId = :conversationId ORDER BY createdAt DESC LIMIT 300")
    fun observeForConversation(conversationId: String): Flow<List<ActivityLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ActivityLogEntity)
}

@Dao
interface WorkspaceFileDao {
    @Query("SELECT * FROM workspace_files ORDER BY name ASC")
    fun observeAll(): Flow<List<WorkspaceFileEntity>>

    @Query("SELECT * FROM workspace_files WHERE conversationId = :conversationId ORDER BY name ASC")
    fun observeForConversation(conversationId: String): Flow<List<WorkspaceFileEntity>>

    @Query("SELECT * FROM workspace_files WHERE conversationId = :conversationId AND name = :name LIMIT 1")
    suspend fun get(conversationId: String, name: String): WorkspaceFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WorkspaceFileEntity)
}

@Dao
interface ScheduledTaskDao {
    @Query("SELECT * FROM scheduled_tasks ORDER BY nextRunAt ASC")
    fun observeAll(): Flow<List<ScheduledTaskEntity>>
    fun observeAll(): Flow<List<ScheduledTaskEntity>>

    @Query("SELECT * FROM scheduled_tasks WHERE conversationId = :conversationId ORDER BY nextRunAt ASC")
    fun observeForConversation(conversationId: String): Flow<List<ScheduledTaskEntity>>

    @Query("SELECT * FROM scheduled_tasks WHERE id = :id LIMIT 1")
    suspend fun get(id: String): ScheduledTaskEntity?

    @Query("UPDATE scheduled_tasks SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ScheduledTaskEntity)
}

@Dao
interface ToolApprovalDao {
    @Query("SELECT * FROM tool_approvals WHERE status = 'PENDING' ORDER BY createdAt DESC")
    fun observePending(): Flow<List<ToolApprovalEntity>>

    @Query("SELECT * FROM tool_approvals WHERE conversationId = :conversationId ORDER BY createdAt DESC")
    fun observeForConversation(conversationId: String): Flow<List<ToolApprovalEntity>>

    @Query("SELECT * FROM tool_approvals WHERE id = :id LIMIT 1")
    suspend fun get(id: String): ToolApprovalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ToolApprovalEntity)
}


@Dao
interface McpServerDao {
    @Query("SELECT * FROM mcp_servers ORDER BY name ASC")
    fun observeAll(): Flow<List<McpServerEntity>>

    @Query("SELECT * FROM mcp_servers WHERE id = :id LIMIT 1")
    suspend fun get(id: String): McpServerEntity?

    @Query("SELECT * FROM mcp_servers WHERE enabled = 1 ORDER BY name ASC")
    suspend fun enabled(): List<McpServerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: McpServerEntity)

    @Query("UPDATE mcp_servers SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)
}


@Dao
interface AgentProfileDao {
    @Query("SELECT * FROM agent_profiles ORDER BY name ASC")
    fun observeAll(): Flow<List<AgentProfileEntity>>

    @Query("SELECT * FROM agent_profiles WHERE id = :id LIMIT 1")
    suspend fun get(id: String): AgentProfileEntity?

    @Query("SELECT * FROM agent_profiles WHERE enabled = 1 ORDER BY name ASC")
    suspend fun enabled(): List<AgentProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: AgentProfileEntity)

    @Query("UPDATE agent_profiles SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long)
}


@Dao
interface ToolPermissionDao {
    @Query("SELECT * FROM tool_permissions WHERE conversationId = :conversationId OR conversationId IS NULL ORDER BY toolName ASC")
    fun observeForConversation(conversationId: String): Flow<List<ToolPermissionEntity>>

    @Query("SELECT * FROM tool_permissions WHERE conversationId = :conversationId AND toolName = :toolName LIMIT 1")
    suspend fun get(conversationId: String, toolName: String): ToolPermissionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ToolPermissionEntity)
}
