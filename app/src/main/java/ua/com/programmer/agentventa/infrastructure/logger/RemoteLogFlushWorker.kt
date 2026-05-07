package ua.com.programmer.agentventa.infrastructure.logger

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Periodic safety net for [RemoteLogUploader]. The uploader runs in-process and
 * drains as soon as new entries arrive, but if the process is killed mid-batch
 * (or the device is offline at the time), entries sit in the table until the
 * next user interaction. This worker drains them every 15 minutes.
 */
@HiltWorker
class RemoteLogFlushWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val uploader: RemoteLogUploader,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            uploader.flushNow()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "debug_log_flush"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<RemoteLogFlushWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
