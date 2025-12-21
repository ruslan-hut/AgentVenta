package ua.com.programmer.agentventa.infrastructure.websocket

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import ua.com.programmer.agentventa.utility.Constants
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for periodic WebSocket sync checks.
 * Runs in background even when app is closed to ensure data is synced.
 *
 * Triggers:
 * - Every N minutes (configurable idle interval)
 * - Only when network is available
 * - Checks for pending data and connects if needed
 */
@HiltWorker
class WebSocketSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val connectionManager: WebSocketConnectionManager,
    private val pendingDataChecker: PendingDataChecker,
    private val logger: Logger
) : CoroutineWorker(context, workerParams) {

    private val TAG = "WebSocketSyncWorker"

    override suspend fun doWork(): Result {
        logger.d(TAG, "Periodic sync worker started")

        return try {
            // Check for pending data
            val hasPendingData = pendingDataChecker.hasPendingData()

            if (hasPendingData) {
                logger.d(TAG, "Has pending data, triggering sync")
                connectionManager.checkAndConnect()
            } else {
                logger.d(TAG, "No pending data, checking if idle interval elapsed")
                connectionManager.checkAndConnect()
            }

            Result.success()
        } catch (e: Exception) {
            logger.e(TAG, "Sync worker error: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "websocket_periodic_sync"

        /**
         * Schedule periodic sync worker.
         * @param context Application context
         * @param intervalMinutes Repeat interval in minutes (minimum 15 for WorkManager)
         */
        fun schedule(context: Context, intervalMinutes: Long) {
            // WorkManager has a minimum interval of 15 minutes
            val effectiveInterval = intervalMinutes.coerceAtLeast(15)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<WebSocketSyncWorker>(
                effectiveInterval, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        /**
         * Schedule with default interval from Constants.
         */
        fun scheduleWithDefaultInterval(context: Context) {
            val intervalMinutes = Constants.WEBSOCKET_IDLE_INTERVAL_DEFAULT / (60 * 1000)
            schedule(context, intervalMinutes)
        }

        /**
         * Cancel the periodic sync worker.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /**
         * Request an immediate one-time sync.
         */
        fun syncNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<WebSocketSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
