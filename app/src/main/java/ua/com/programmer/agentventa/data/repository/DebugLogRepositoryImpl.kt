package ua.com.programmer.agentventa.data.repository

import ua.com.programmer.agentventa.data.local.dao.DebugLogDao
import ua.com.programmer.agentventa.data.local.entity.DebugLogEntry
import ua.com.programmer.agentventa.domain.repository.DebugLogRepository
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicInteger

@Singleton
class DebugLogRepositoryImpl @Inject constructor(
    private val dao: DebugLogDao,
) : DebugLogRepository {

    // Cap: drop the oldest 500 once table grows past 5000.
    // Counts are checked every CAP_CHECK_EVERY inserts to amortize the COUNT(*) cost.
    private val insertCounter = AtomicInteger(0)

    override suspend fun record(level: String, tag: String, message: String, fieldsJson: String) {
        val entry = DebugLogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            fields = fieldsJson.ifBlank { "{}" }
        )
        dao.insert(entry)

        if (insertCounter.incrementAndGet() % CAP_CHECK_EVERY == 0) {
            val total = dao.count()
            if (total > MAX_ROWS) {
                dao.dropOldest(DROP_BATCH)
            }
        }
    }

    override suspend fun pending(limit: Int): List<DebugLogEntry> = dao.pending(limit)

    override suspend fun markSent(ids: List<Long>) {
        if (ids.isEmpty()) return
        dao.markSent(ids)
    }

    override suspend fun bumpAttempts(ids: List<Long>) {
        if (ids.isEmpty()) return
        dao.bumpAttempts(ids)
    }

    override suspend fun pendingCount(): Int = dao.pendingCount()

    override suspend fun totalCount(): Int = dao.count()

    override suspend fun clearSent(): Int = dao.deleteSent()

    companion object {
        const val MAX_ROWS = 5000
        const val DROP_BATCH = 500
        const val CAP_CHECK_EVERY = 64
    }
}
