package ua.com.programmer.agentventa.data.websocket

import com.google.gson.JsonObject

/**
 * WebSocket Document Synchronization Models
 *
 * These models are used for sending and receiving documents via WebSocket
 * relay server instead of direct HTTP to 1C.
 */

/**
 * Document upload wrapper for sending documents to the relay server
 *
 * @param documentType Type of document ("order", "cash", "image", "location")
 * @param guid Unique identifier for the document
 * @param data Document data (will be serialized Order, Cash, etc.)
 */
data class DocumentUpload(
    val documentType: String,
    val guid: String,
    val data: JsonObject
)

/**
 * Order upload payload
 * Contains order header and content lines
 */
data class OrderUploadPayload(
    val order: JsonObject,
    val content: List<JsonObject>
)

/**
 * Cash receipt upload payload
 */
data class CashUploadPayload(
    val cash: JsonObject
)

/**
 * Image upload payload
 * Contains image metadata and base64-encoded data
 */
data class ImageUploadPayload(
    val guid: String,
    val productGuid: String,
    val imageData: String, // Base64 encoded
    val isLocal: Boolean
)

/**
 * Location history upload payload
 */
data class LocationUploadPayload(
    val locations: List<JsonObject>
)

/**
 * Sync status response from the server
 *
 * @param status Current status ("queued", "processing", "completed", "error")
 * @param documentGuid GUID of the document this status refers to
 * @param message Optional message with additional details
 * @param timestamp Server timestamp
 */
data class SyncStatusResponse(
    val status: String,
    val documentGuid: String,
    val message: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Catalog update notification from the server
 *
 * @param catalogType Type of catalog ("clients", "products", "debts", "companies", "stores", "rests", "prices", "images")
 * @param updateType Type of update ("full", "incremental")
 * @param data List of catalog items as JSON objects
 * @param timestamp Server timestamp of the update
 */
data class CatalogUpdate(
    val catalogType: String,
    val updateType: String, // "full" or "incremental"
    val data: List<JsonObject>,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Sync result summary
 * Contains counts of uploaded/downloaded items
 */
data class WebSocketSyncResult(
    val ordersUploaded: Int = 0,
    val cashUploaded: Int = 0,
    val imagesUploaded: Int = 0,
    val locationsUploaded: Int = 0,
    val clientsDownloaded: Int = 0,
    val productsDownloaded: Int = 0,
    val debtsDownloaded: Int = 0,
    val imagesDownloaded: Int = 0,
    val success: Boolean = true,
    val message: String? = null
)

/**
 * Sync progress indicator
 * Used to emit progress updates during sync operations
 */
data class SyncProgress(
    val currentStep: String,
    val totalSteps: Int = 0,
    val completedSteps: Int = 0,
    val percentComplete: Int = 0
)

/**
 * Document sync request
 * Sent to server to initiate document upload
 */
data class DocumentSyncRequest(
    val documentType: String,
    val documentGuid: String,
    val payload: JsonObject
)

/**
 * Catalog sync request
 * Sent to server to request catalog updates
 */
data class CatalogSyncRequest(
    val catalogTypes: List<String>, // List of catalog types to download
    val fullSync: Boolean = false, // If true, download all; if false, only changes since last sync
    val lastSyncTimestamp: Long? = null
)

/**
 * Sync completion notification from server
 * Indicates that all pending sync operations are complete
 */
data class SyncCompleteNotification(
    val success: Boolean,
    val totalDocumentsProcessed: Int,
    val totalCatalogsUpdated: Int,
    val errors: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
