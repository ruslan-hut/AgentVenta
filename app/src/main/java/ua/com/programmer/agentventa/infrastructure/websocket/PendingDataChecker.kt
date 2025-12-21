package ua.com.programmer.agentventa.infrastructure.websocket

import ua.com.programmer.agentventa.data.local.dao.DataExchangeDao
import ua.com.programmer.agentventa.domain.repository.UserAccountRepository
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks for pending data that needs to be synced via WebSocket.
 * Used by WebSocketConnectionManager to determine if connection is needed.
 */
@Singleton
class PendingDataChecker @Inject constructor(
    private val dataExchangeDao: DataExchangeDao,
    private val userAccountRepository: UserAccountRepository,
    private val logger: Logger
) {
    private val TAG = "PendingDataChecker"

    /**
     * Check if there is any pending data to sync.
     * @return true if there are orders, cash, images, or locations waiting to be sent
     */
    suspend fun hasPendingData(): Boolean {
        val accountGuid = getCurrentAccountGuid() ?: return false

        val ordersCount = getPendingOrdersCount(accountGuid)
        val cashCount = getPendingCashCount(accountGuid)
        val imagesCount = getPendingImagesCount(accountGuid)
        val locationsCount = getPendingLocationsCount(accountGuid)

        val hasPending = ordersCount > 0 || cashCount > 0 || imagesCount > 0 || locationsCount > 0

        if (hasPending) {
            logger.d(TAG, "Pending data: orders=$ordersCount, cash=$cashCount, images=$imagesCount, locations=$locationsCount")
        }

        return hasPending
    }

    /**
     * Get summary of all pending data counts.
     */
    suspend fun getPendingDataSummary(): PendingDataSummary {
        val accountGuid = getCurrentAccountGuid() ?: return PendingDataSummary.EMPTY

        return PendingDataSummary(
            ordersCount = getPendingOrdersCount(accountGuid),
            cashCount = getPendingCashCount(accountGuid),
            imagesCount = getPendingImagesCount(accountGuid),
            locationsCount = getPendingLocationsCount(accountGuid)
        )
    }

    /**
     * Count of orders ready to be sent (is_processed=1).
     */
    private suspend fun getPendingOrdersCount(accountGuid: String): Int {
        return try {
            dataExchangeDao.getOrders(accountGuid)?.size ?: 0
        } catch (e: Exception) {
            logger.e(TAG, "Error counting pending orders: ${e.message}")
            0
        }
    }

    /**
     * Count of cash receipts ready to be sent (is_processed=1).
     */
    private suspend fun getPendingCashCount(accountGuid: String): Int {
        return try {
            dataExchangeDao.getCash(accountGuid)?.size ?: 0
        } catch (e: Exception) {
            logger.e(TAG, "Error counting pending cash: ${e.message}")
            0
        }
    }

    /**
     * Count of client images waiting to be uploaded (is_sent=0 AND is_local=1).
     */
    private suspend fun getPendingImagesCount(accountGuid: String): Int {
        return try {
            dataExchangeDao.getClientImages(accountGuid)?.size ?: 0
        } catch (e: Exception) {
            logger.e(TAG, "Error counting pending images: ${e.message}")
            0
        }
    }

    /**
     * Count of client locations waiting to be synced (is_modified=1).
     */
    private suspend fun getPendingLocationsCount(accountGuid: String): Int {
        return try {
            dataExchangeDao.getClientLocations(accountGuid)?.size ?: 0
        } catch (e: Exception) {
            logger.e(TAG, "Error counting pending locations: ${e.message}")
            0
        }
    }

    private suspend fun getCurrentAccountGuid(): String? {
        return try {
            userAccountRepository.currentAccount.first()?.guid
        } catch (e: Exception) {
            logger.e(TAG, "Error getting current account: ${e.message}")
            null
        }
    }
}

/**
 * Summary of pending data counts.
 */
data class PendingDataSummary(
    val ordersCount: Int,
    val cashCount: Int,
    val imagesCount: Int,
    val locationsCount: Int
) {
    val total: Int get() = ordersCount + cashCount + imagesCount + locationsCount
    val hasPendingData: Boolean get() = total > 0

    companion object {
        val EMPTY = PendingDataSummary(0, 0, 0, 0)
    }
}
