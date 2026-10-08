package com.danilatop.aimessenger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        MemoryEntity::class,
        ActivityLogEntity::class,
        WorkspaceFileEntity::class,
        ScheduledTaskEntity::class,
        ToolApprovalEntity::class,
        McpServerEntity::class,
        AgentProfileEntity::class,
        ToolPermissionEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun memories(): MemoryDao
    abstract fun activity(): ActivityDao
    abstract fun files(): WorkspaceFileDao
    abstract fun tasks(): ScheduledTaskDao
    abstract fun approvals(): ToolApprovalDao
    abstract fun mcpServers(): McpServerDao
    abstract fun agentProfiles(): AgentProfileDao
    abstract fun toolPermissions(): ToolPermissionDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ai_messenger_21.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
