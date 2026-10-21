package com.lucabol.onelinejournal.cloud

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lucabol.onelinejournal.JournalApplication
import com.lucabol.onelinejournal.userMessage
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class BackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as JournalApplication).graph
        return try {
            graph.ready.await()
            graph.cloud.uploadPending()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            graph.store.updateState { it.copy(cloudMessage = userMessage(e)) }
            val transient = e is IOException && (e !is DriveHttpException || e.status == 429 || e.status >= 500)
            if (transient && runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    companion object {
        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<BackupWorker>().setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("journal-backup",
                ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        fun ensurePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("journal-backup-recovery",
                ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
