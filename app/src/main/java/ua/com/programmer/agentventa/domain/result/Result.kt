package ua.com.programmer.agentventa.domain.result

/**
 * Domain layer result wrapper for use cases.
 * Provides type-safe success/error handling.
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: DomainException) : Result<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = (this as? Success)?.data

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw exception
    }

    fun exceptionOrNull(): DomainException? = (this as? Error)?.exception

    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }

    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(data)
        is Error -> this
    }
}

/**
 * Domain exceptions for typed error handling.
 */
sealed class DomainException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {

    /**
     * Network-related errors (connection, timeout, server errors).
     */
    data class NetworkError(
        override val message: String,
        val code: Int = -1,
        val isRetryable: Boolean = true,
        override val cause: Throwable? = null
    ) : DomainException(message, cause)

    /**
     * Database operation errors.
     */
    data class DatabaseError(
        override val message: String,
        override val cause: Throwable? = null
    ) : DomainException(message, cause)

    /**
     * Validation errors with field information.
     */
    data class ValidationError(
        val field: String,
        override val message: String
    ) : DomainException(message)

    /**
     * Authentication/authorization errors.
     */
    data class AuthenticationError(
        override val message: String,
        val shouldLogout: Boolean = false
    ) : DomainException(message)

    /**
     * Business logic errors.
     */
    data class BusinessError(
        override val message: String,
        override val cause: Throwable? = null
    ) : DomainException(message, cause)

    /**
     * Resource not found errors.
     */
    data class NotFoundError(
        val resourceType: String,
        val resourceId: String
    ) : DomainException("$resourceType not found: $resourceId")
}

/**
 * Extension functions for Result handling.
 */
inline fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    if (this is Result.Success) action(data)
    return this
}

inline fun <T> Result<T>.onError(action: (DomainException) -> Unit): Result<T> {
    if (this is Result.Error) action(exception)
    return this
}

/**
 * Helper to wrap suspend calls in Result.
 */
inline fun <T> runCatching(block: () -> T): Result<T> {
    return try {
        Result.Success(block())
    } catch (e: DomainException) {
        Result.Error(e)
    } catch (e: Exception) {
        Result.Error(DomainException.BusinessError(e.message ?: "Unknown error", e))
    }
}
