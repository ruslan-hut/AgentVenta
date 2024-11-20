package ua.com.programmer.agentventa.repository

interface CommonRepository {
    suspend fun cleanup(from: Long): Int
}