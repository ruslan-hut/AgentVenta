package ua.com.programmer.agentventa.domain.repository

interface CommonRepository {
    suspend fun cleanup(from: Long): Int
}