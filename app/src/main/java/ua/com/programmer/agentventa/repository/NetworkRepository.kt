package ua.com.programmer.agentventa.repository

import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.http.Result
import java.io.File

interface NetworkRepository {
    //suspend fun refreshToken(): String
    suspend fun updateAll(): Flow<Result>
    suspend fun updateDifferential(): Flow<Result>
    suspend fun getDebtContent(type: String, guid: String): Flow<Result>
    suspend fun getPrintData(guid: String, storage: File): Flow<Result>
}