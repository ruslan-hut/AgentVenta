package ua.com.programmer.agentventa.data.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import ua.com.programmer.agentventa.data.local.entity.UserAccount
import ua.com.programmer.agentventa.data.remote.api.HttpClientApi
import ua.com.programmer.agentventa.extensions.trimForLog
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import ua.com.programmer.agentventa.domain.repository.UserAccountRepository
import ua.com.programmer.agentventa.utility.XMap
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of TokenManager using proper coroutines.
 * Eliminates dangerous runBlocking calls from NetworkRepositoryImpl.
 *
 * Key improvements:
 * - Uses suspend functions with proper dispatchers
 * - Mutex prevents concurrent token refreshes
 * - Timeout prevents infinite blocking
 * - Dedicated single-thread executor for sync operations (avoids ANR on shared pools)
 * - Clear separation of sync vs async refresh
 */
@Singleton
class TokenManagerImpl @Inject constructor(
    private val userAccountRepository: UserAccountRepository,
    private val logger: Logger
) : TokenManager {

    private val logTag = "TokenManager"

    private var account: UserAccount? = null
    private var apiService: HttpClientApi? = null
    private var currentToken: String = ""

    private var refreshCounter: Int = 0
    private val maxTokenRefresh = 3
    private val tokenRefreshTimeout = 10_000L // 10 seconds

    // Mutex to prevent concurrent token refreshes
    private val refreshMutex = Mutex()

    // Dedicated single-thread dispatcher for synchronous token operations
    // This prevents runBlocking from blocking shared IO thread pool
    private val tokenRefreshExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "TokenRefresh-Thread").apply { isDaemon = true }
    }
    private val tokenRefreshDispatcher = tokenRefreshExecutor.asCoroutineDispatcher()

    // Dedicated scope for token operations
    private val tokenScope = CoroutineScope(Dispatchers.IO)

    override fun configure(account: UserAccount?) {
        this.account = account
        this.currentToken = account?.token ?: ""
        resetCounter()
    }

    fun setApiService(service: HttpClientApi) {
        this.apiService = service
    }

    override fun getCurrentToken(): String {
        return currentToken
    }

    override suspend fun refreshToken(tag: String): TokenManager.TokenResult {
        return refreshMutex.withLock {
            refreshTokenInternal(tag)
        }
    }

    override fun refreshTokenSync(tag: String): String {
        // This method is called by OkHttp Authenticator which is synchronous.
        // We use runBlocking here but in a controlled, safe way:
        // 1. Protected by mutex to prevent concurrent refreshes
        // 2. Has timeout to prevent infinite blocking
        // 3. Runs on DEDICATED single-thread dispatcher (not shared IO pool)
        // 4. Only used by Authenticator, not general application code
        //
        // Using a dedicated thread ensures:
        // - No ANR risk on main thread
        // - No blocking of shared Dispatchers.IO thread pool
        // - Predictable, isolated execution

        return try {
            runBlocking(tokenRefreshDispatcher) {
                withTimeout(tokenRefreshTimeout) {
                    when (val result = refreshToken(tag)) {
                        is TokenManager.TokenResult.Success -> {
                            if (result.canRead) result.token else ""
                        }
                        is TokenManager.TokenResult.Error -> {
                            logger.w(logTag, "$tag: ${result.message}")
                            ""
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.e(logTag, "$tag: refreshTokenSync failed: ${e.message}")
            ""
        }
    }

    override suspend fun clearToken(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                currentToken = ""
                account?.let {
                    logger.w(logTag, "Clearing token due to error")
                    val updatedAccount = it.copy(token = "")
                    userAccountRepository.saveAccount(updatedAccount)
                    account = updatedAccount
                }
                true
            } catch (e: Exception) {
                logger.e(logTag, "Failed to clear token: ${e.message}")
                false
            }
        }
    }

    override fun resetCounter() {
        refreshCounter = 0
    }

    override fun isRefreshLimitReached(): Boolean {
        return refreshCounter >= maxTokenRefresh
    }

    /**
     * Internal token refresh implementation.
     * Must be called within mutex lock.
     */
    private suspend fun refreshTokenInternal(tag: String): TokenManager.TokenResult {
        return withContext(Dispatchers.IO) {
            refreshCounter++

            if (isRefreshLimitReached()) {
                val message = "$tag: Token refresh limit ($maxTokenRefresh) reached"
                logger.w(logTag, message)
                return@withContext TokenManager.TokenResult.Error(
                    message,
                    Exception("Token refresh limit reached")
                )
            }

            val accountGuid = account?.guid
            if (accountGuid.isNullOrEmpty()) {
                val message = "Account GUID is empty"
                logger.w(logTag, "$tag: $message")
                return@withContext TokenManager.TokenResult.Error(
                    message,
                    Exception(message)
                )
            }

            val service = apiService
            if (service == null) {
                val message = "API service not configured"
                logger.e(logTag, "$tag: $message")
                return@withContext TokenManager.TokenResult.Error(
                    message,
                    Exception(message)
                )
            }

            try {
                // Call API to refresh token
                val response = service.check(accountGuid)
                val options = XMap(response as Map<*, *>)

                val newToken = options.getString("token")
                val canRead = options.getBoolean("read")
                val license = options.getString("license")

                if (newToken.isBlank()) {
                    val message = "Received empty token from server"
                    logger.w(logTag, "$tag: $message")
                    return@withContext TokenManager.TokenResult.Error(message)
                }

                logger.d(logTag, "$tag: Token received: ${newToken.trimForLog()}")

                // Update account with new token
                account?.let {
                    val updatedAccount = it.copy(
                        token = newToken,
                        options = options.toJson(),
                        license = license
                    )
                    userAccountRepository.saveAccount(updatedAccount)
                    account = updatedAccount
                    currentToken = newToken
                }

                if (!canRead) {
                    val message = "User has no read access"
                    logger.w(logTag, "$tag: $message")
                    return@withContext TokenManager.TokenResult.Error(message)
                }

                TokenManager.TokenResult.Success(newToken, canRead)

            } catch (e: Exception) {
                val message = "Token refresh failed: ${e.message}"
                logger.e(logTag, "$tag: $message")
                TokenManager.TokenResult.Error(message, e)
            }
        }
    }
}
