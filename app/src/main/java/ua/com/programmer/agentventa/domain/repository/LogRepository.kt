package ua.com.programmer.agentventa.domain.repository

import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.data.local.entity.LogEvent

interface LogRepository {
    suspend fun log(level: String, tag: String, message: String)
    fun fetchLogs(): Flow<List<LogEvent>>
    suspend fun readLogs(): List<LogEvent>
    suspend fun cleanUp(): Int
}