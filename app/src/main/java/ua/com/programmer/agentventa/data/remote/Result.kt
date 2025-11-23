package ua.com.programmer.agentventa.data.remote

sealed class Result {
    data class Success(val message: String): Result()
    data class Error(val message: String): Result()
    data class Progress(val message: String): Result()
}
