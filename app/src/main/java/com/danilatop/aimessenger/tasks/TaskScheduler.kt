package com.danilatop.aimessenger.tasks

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object TaskScheduler {
    fun schedule(context: Context, taskId: String, delayMinutes: Long) {
        val request = OneTimeWorkRequestBuilder<AiTaskWorker>()
            .setInputData(Data.Builder().putString("task_id", taskId).build())
            .setInitialDelay(delayMinutes.coerceAtLeast(1L), TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("ai-messenger-task")
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }
}
