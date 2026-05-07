package ua.com.programmer.agentventa.domain.repository

import ua.com.programmer.agentventa.data.local.entity.DebugLogEntry

/**
 * Persists structured WebSocket debug log lines for upload to the relay server.
 * Independent of [LogRepository] — that table backs the user-visible log screen
 * and uses its own retention policy. This one is a ring buffer bounded by row
 * count, drained by [ua.com.programmer.agentventa.infrastructure.logger.RemoteLogUploader].
 */
interface DebugLogRepository {

    suspend fun record(level: String, tag: String, message: String, fieldsJson: String)

    suspend fun pending(limit: Int): List<DebugLogEntry>

    suspend fun markSent(ids: List<Long>)

    suspend fun bumpAttempts(ids: List<Long>)

    suspend fun pendingCount(): Int

    suspend fun totalCount(): Int

    suspend fun clearSent(): Int
}
