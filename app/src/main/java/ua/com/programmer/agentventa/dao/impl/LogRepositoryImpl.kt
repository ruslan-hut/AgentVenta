package ua.com.programmer.agentventa.dao.impl

import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.dao.LogDao
import ua.com.programmer.agentventa.dao.entity.LogEvent
import ua.com.programmer.agentventa.extensions.beginOfDay
import ua.com.programmer.agentventa.license.LicenseManager
import ua.com.programmer.agentventa.repository.LogRepository
import java.util.GregorianCalendar
import javax.inject.Inject

class LogRepositoryImpl @Inject constructor(
    private val logDao: LogDao,
    private val lm: LicenseManager,
) : LogRepository {
    override suspend fun log(level: String, tag: String, message: String) {
        val event = LogEvent(
            0,
            System.currentTimeMillis(),
            level,
            tag,
            message
        )
        // local database record
        logDao.insertLogEvent(event)
        // cloud logging (if enabled)
        lm.log(event)
    }

    override fun fetchLogs(): Flow<List<LogEvent>> {
        return logDao.fetchLogs()
    }

    override suspend fun readLogs(): List<LogEvent> {
        return logDao.readLogs() ?: emptyList()
    }

    override suspend fun cleanUp(): Int {
        val today = GregorianCalendar.getInstance().time.beginOfDay()
        return logDao.deleteOldEvents(today)
    }
}