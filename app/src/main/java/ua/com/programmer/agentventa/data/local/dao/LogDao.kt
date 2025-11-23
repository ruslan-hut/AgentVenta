package ua.com.programmer.agentventa.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.data.local.entity.LogEvent

@Dao
interface LogDao {
    @Insert
    suspend fun insertLogEvent(logEvent: LogEvent): Long

    @Query("SELECT * FROM log_events ORDER BY id DESC")
    fun fetchLogs(): Flow<List<LogEvent>>

    @Query("SELECT * FROM log_events ORDER BY id DESC LIMIT 50")
    suspend fun readLogs(): List<LogEvent>?

    // The number 604800000 is the number of milliseconds in a week (7 * 24 * 60 * 60 * 1000)
    @Query("DELETE FROM log_events WHERE timestamp < (:currentTimestamp - 604800000)")
    suspend fun deleteOldEvents(currentTimestamp: Long): Int
}