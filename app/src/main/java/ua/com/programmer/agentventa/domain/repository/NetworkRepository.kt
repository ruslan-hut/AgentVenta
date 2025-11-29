package ua.com.programmer.agentventa.domain.repository

import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.data.local.entity.Cash
import ua.com.programmer.agentventa.data.local.entity.LocationHistory
import ua.com.programmer.agentventa.data.local.entity.Order
import ua.com.programmer.agentventa.data.local.entity.LOrderContent
import ua.com.programmer.agentventa.data.local.entity.ProductImage
import ua.com.programmer.agentventa.data.remote.Result
import ua.com.programmer.agentventa.data.websocket.WebSocketSyncResult
import java.io.File

interface NetworkRepository {
    //suspend fun refreshToken(): String
    suspend fun updateAll(): Flow<Result>
    suspend fun updateDifferential(): Flow<Result>
    suspend fun getDebtContent(type: String, guid: String): Flow<Result>
    suspend fun getPrintData(guid: String, storage: File): Flow<Result>

    /**
     * WebSocket Document Synchronization Methods
     * These methods are used when UserAccount.shouldUseWebSocket() returns true
     */

    /**
     * Upload a single order with its content via WebSocket
     * @param order Order header
     * @param orderContent List of order line items (LOrderContent from DAO)
     * @return Flow emitting upload status and result
     */
    suspend fun uploadOrderViaWebSocket(order: Order, orderContent: List<LOrderContent>): Flow<Result>

    /**
     * Upload a single cash receipt via WebSocket
     * @param cash Cash receipt
     * @return Flow emitting upload status and result
     */
    suspend fun uploadCashViaWebSocket(cash: Cash): Flow<Result>

    /**
     * Upload product images via WebSocket
     * @param images List of product images to upload
     * @return Flow emitting upload status and result
     */
    suspend fun uploadImagesViaWebSocket(images: List<ProductImage>): Flow<Result>

    /**
     * Upload location history via WebSocket
     * @param locations List of location history records
     * @return Flow emitting upload status and result
     */
    suspend fun uploadLocationsViaWebSocket(locations: List<LocationHistory>): Flow<Result>

    /**
     * Download all catalogs via WebSocket
     * Catalogs include: clients, products, debts, companies, stores, rests, prices, images
     * @param fullSync If true, download all data; if false, only changes since last sync
     * @return Flow emitting download progress and result
     */
    suspend fun downloadCatalogsViaWebSocket(fullSync: Boolean = false): Flow<Result>

    /**
     * Perform full document sync via WebSocket
     * Uploads all unsent documents and downloads catalog updates
     * @return Flow emitting sync progress and final result
     */
    suspend fun syncViaWebSocket(): Flow<Result>
}