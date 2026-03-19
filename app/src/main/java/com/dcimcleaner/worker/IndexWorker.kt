package com.dcimcleaner.worker

import android.content.Context
import androidx.work.*
import com.dcimcleaner.data.repository.PhotoRepository

class IndexWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val PROGRESS_KEY = "progress"
        const val WORK_NAME = "dcim_index_work"

        // First-time index — skip if already running
        fun enqueue(context: Context): OneTimeWorkRequest {
            val request = OneTimeWorkRequestBuilder<IndexWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
            return request
        }

        // Re-index from Settings — always replace
        fun enqueueForce(context: Context): OneTimeWorkRequest {
            val request = OneTimeWorkRequestBuilder<IndexWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
            return request
        }
    }

    override suspend fun doWork(): Result {
        val repo = PhotoRepository(applicationContext)
        return try {
            repo.buildIndex { progress ->
                setProgressAsync(workDataOf(PROGRESS_KEY to progress))
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
