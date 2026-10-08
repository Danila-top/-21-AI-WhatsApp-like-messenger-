package com.danilatop.aimessenger.tasks

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.danilatop.aimessenger.ai.AIProvider
import com.danilatop.aimessenger.agent.AgentRuntime
import com.danilatop.aimessenger.data.AppDatabase
import com.danilatop.aimessenger.security.SecureStore

class AiTaskWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString("task_id")
            ?: return Result.failure()

        val db = AppDatabase.get(applicationContext)
        val task = db.tasks().get(taskId)
            ?: return Result.failure()

        if (!task.enabled) return Result.success()

        return runCatching {
            val conversationId = task.conversationId
                ?: error("Scheduled AI task has no conversation.")
            val runtime = AgentRuntime(
                applicationContext,
                db,
                AIProvider(SecureStore(applicationContext))
            )
            runtime.runScheduledTask(conversationId, task.prompt)
            db.tasks().setEnabled(task.id, false)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}
