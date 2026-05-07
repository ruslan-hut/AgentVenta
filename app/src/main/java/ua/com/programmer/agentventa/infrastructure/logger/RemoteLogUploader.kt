package ua.com.programmer.agentventa.infrastructure.logger

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import ua.com.programmer.agentventa.BuildConfig
import ua.com.programmer.agentventa.data.local.entity.DebugLogEntry
import ua.com.programmer.agentventa.data.remote.api.DebugLogApi
import ua.com.programmer.agentventa.data.remote.dto.DebugLogEntryDto
import ua.com.programmer.agentventa.data.remote.dto.DebugLogUploadDto
import ua.com.programmer.agentventa.domain.repository.DebugLogRepository
import ua.com.programmer.agentventa.domain.repository.UserAccountRepository
import ua.com.programmer.agentventa.infrastructure.config.ApiKeyProvider
import ua.com.programmer.agentventa.utility.Constants
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drains [DebugLogRepository] into the relay server's `/api/v1/device/logs`
 * endpoint over plain HTTP — independent of the WebSocket so it still works
 * (and is useful) when WS is broken.
 *
 * Lifecycle:
 *   - [start] is called from [ua.com.programmer.agentventa.presentation.main.AgentApplication.onCreate].
 *     It is idempotent.
 *   - The drain loop wakes on three signals (merged): the [RemoteLogTickle] from
 *     [RemoteLogSink], a [DebugLogToggle] flip from off→on, and explicit
 *     [flushNow] requests from the periodic [RemoteLogFlushWorker].
 *   - When the toggle is off, no work happens; pending rows stay in the table
 *     until the toggle is flipped on (or until the ring-buffer cap evicts them).
 *
 * Backoff:
 *   - Transient failure: 5s → 10s → 30s → 60s → 5min cap, until success.
 *   - 4xx (except 429): drop the batch (mark as sent) — avoids a permanent jam
 *     on a bad row.
 */
@Singleton
class RemoteLogUploader @Inject constructor(
    private val repository: DebugLogRepository,
    private val toggle: DebugLogToggle,
    private val tickle: RemoteLogTickle,
    private val api: DebugLogApi,
    private val userAccountRepository: UserAccountRepository,
    private val apiKeyProvider: ApiKeyProvider,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    private val drainMutex = Mutex()
    private var loopJob: Job? = null

    @Volatile private var backoffMs: Long = 0L

    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            // React to: tickle from sink, toggle flip, explicit flush requests.
            // collectLatest restarts on every emission so a long delay is
            // interrupted by a new signal — exactly what we want for debouncing.
            merge(
                tickle.asFlow(),
                toggle.enabled.let { flow ->
                    kotlinx.coroutines.flow.flow {
                        flow.collect { if (it) emit(Unit) }
                    }
                },
                flushSignals,
            ).collectLatest {
                if (!toggle.isEnabled()) return@collectLatest
                delay(Constants.DEBUG_LOG_DEBOUNCE_MS)
                drainAll()
            }
        }
    }

    suspend fun flushNow() {
        flushSignals.emit(Unit)
        drainAll()
    }

    private suspend fun drainAll() = drainMutex.withLock {
        if (!toggle.isEnabled()) return
        if (!apiKeyProvider.hasBackendHost() || !apiKeyProvider.hasWebSocketApiKey()) return

        while (toggle.isEnabled()) {
            val batch = repository.pending(Constants.DEBUG_LOG_BATCH_LIMIT)
            if (batch.isEmpty()) {
                backoffMs = 0L
                return
            }

            val account = userAccountRepository.getCurrent()
            if (account == null || account.guid.isBlank()) {
                // No identity → cannot authenticate. Stop trying until next
                // signal; account changes will trigger toggle re-evaluation.
                return
            }

            val payload = buildPayload(batch, account.guid, account.description)
            val authHeader = "Bearer ${apiKeyProvider.webSocketApiKey}:${account.guid}"

            val outcome = try {
                val response = api.uploadLogs(authHeader, payload)
                when {
                    response.isSuccessful -> Outcome.Success
                    response.code() == 429 -> Outcome.RetryLater
                    response.code() in 400..499 -> Outcome.DropBatch(response.code())
                    else -> Outcome.RetryLater
                }
            } catch (e: HttpException) {
                if (e.code() in 400..499 && e.code() != 429) Outcome.DropBatch(e.code())
                else Outcome.RetryLater
            } catch (_: Throwable) {
                Outcome.RetryLater
            }

            when (outcome) {
                Outcome.Success -> {
                    repository.markSent(batch.map { it.id })
                    repository.clearSent() // keep DB tidy; rows are now redundant
                    backoffMs = 0L
                    // loop again to catch up if more pending exists
                }
                is Outcome.DropBatch -> {
                    // Poison: mark as sent so we don't loop on the same broken
                    // payload forever. The 4xx itself is a useful signal already
                    // observable in the local user-visible log.
                    repository.markSent(batch.map { it.id })
                    repository.clearSent()
                    backoffMs = 0L
                }
                Outcome.RetryLater -> {
                    repository.bumpAttempts(batch.map { it.id })
                    backoffMs = nextBackoff(backoffMs)
                    delay(backoffMs)
                    // loop again — collectLatest does not interrupt drainAll
                    // once entered (we hold the mutex), but a new signal emitted
                    // during the delay will queue another drain.
                }
            }
        }
    }

    private fun buildPayload(
        batch: List<DebugLogEntry>,
        deviceUuid: String,
        accountDescription: String,
    ): DebugLogUploadDto {
        // Bound serialized size at DEBUG_LOG_BATCH_BYTES by trimming trailing
        // entries — naive char-count, conservatively under-estimates. Anything
        // dropped here remains in the DB and ships in the next batch.
        val budget = Constants.DEBUG_LOG_BATCH_BYTES
        val accepted = ArrayList<DebugLogEntryDto>(batch.size)
        var size = 256 // header overhead estimate
        for (e in batch) {
            val dto = DebugLogEntryDto(
                timestamp = e.timestamp,
                level = e.level,
                tag = e.tag,
                message = e.message,
                fields = parseFields(e.fields),
            )
            val rough = e.tag.length + e.message.length + e.fields.length + 64
            if (size + rough > budget && accepted.isNotEmpty()) break
            accepted.add(dto)
            size += rough
        }
        return DebugLogUploadDto(
            device_uuid = deviceUuid,
            account_description = accountDescription,
            app_version = BuildConfig.VERSION_NAME,
            app_version_code = BuildConfig.VERSION_CODE,
            entries = accepted,
        )
    }

    private fun parseFields(json: String): JsonObject? {
        if (json.isBlank() || json == "{}") return null
        return try {
            JsonParser.parseString(json).asJsonObject
        } catch (_: Throwable) {
            null
        }
    }

    private fun nextBackoff(prev: Long): Long = when {
        prev <= 0L -> 5_000L
        prev < 10_000L -> 10_000L
        prev < 30_000L -> 30_000L
        prev < 60_000L -> 60_000L
        else -> 300_000L
    }

    private sealed interface Outcome {
        data object Success : Outcome
        data object RetryLater : Outcome
        data class DropBatch(val code: Int) : Outcome
    }
}
