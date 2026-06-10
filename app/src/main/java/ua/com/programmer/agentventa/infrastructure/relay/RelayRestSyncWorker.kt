package ua.com.programmer.agentventa.infrastructure.relay

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import ua.com.programmer.agentventa.data.local.entity.isRelayRest
import ua.com.programmer.agentventa.data.remote.Result as SyncResult
import ua.com.programmer.agentventa.data.repository.RelaySyncClient
import ua.com.programmer.agentventa.domain.repository.UserAccountRepository
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import ua.com.programmer.agentventa.utility.Constants
import java.util.concurrent.TimeUnit

/**
 * Periodic background sync for REST-relay accounts: uploads pending documents
 * and pulls pushed catalog while the app is backgrounded. No-ops for direct-1C
 * and demo accounts (those sync over manual HTTP).
 *
 * WorkManager's 15-minute floor applies; sub-15-minute freshness needs the
 * (deferred) FCM doorbell.
 */
@HiltWorker
class RelayRestSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val userAccountRepository: UserAccountRepository,
    private val relaySyncClient: RelaySyncClient,
    private val logger: Logger,
) : CoroutineWorker(context, workerParams) {

    private val tag = "AV-RelayRestSyncWorker"

    override suspend fun doWork(): Result {
        val account = userAccountRepository.getCurrent()
        if (account == null || !account.isRelayRest()) {
            // Not a REST account — WS worker / manual HTTP handle those.
            return Result.success()
        }

        val (approved, message) = relaySyncClient.checkApproval(account)
        if (!approved) {
            // Pending/denied/license error — not retryable from a worker. The
            // user resolves approval; the next tick or a manual sync proceeds.
            logger.d(tag, "REST sync skipped: $message")
            return Result.success()
        }

        var hadError = false
        return try {
            relaySyncClient.uploadDocuments(account).collect {
                if (it is SyncResult.Error) hadError = true
            }
            relaySyncClient.pullCatalog(account).collect {
                if (it is SyncResult.Error) hadError = true
            }
            if (hadError && runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(tag, "REST sync worker error: ${e.message}")
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "relay_rest_periodic_sync"
        private const val MAX_RETRY_ATTEMPTS = 5

        fun schedule(
            context: Context,
            intervalMinutes: Long,
            policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP
        ) {
            val effectiveInterval = intervalMinutes.coerceAtLeast(15)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<RelayRestSyncWorker>(
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
                policy,
                request
            )
        }

        // Default cadence (15 min). KEEP so app-start re-scheduling doesn't reset it.
        fun scheduleWithDefaultInterval(context: Context) {
            val intervalMinutes = Constants.WEBSOCKET_IDLE_INTERVAL_DEFAULT / (60 * 1000)
            schedule(context, intervalMinutes, ExistingPeriodicWorkPolicy.KEEP)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        fun syncNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<RelayRestSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
