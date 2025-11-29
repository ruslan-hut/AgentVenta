package ua.com.programmer.agentventa.data.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.withTimeoutOrNull
import ua.com.programmer.agentventa.data.websocket.*
import ua.com.programmer.agentventa.domain.repository.SettingsSyncRepository
import ua.com.programmer.agentventa.domain.repository.WebSocketRepository
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import ua.com.programmer.agentventa.presentation.features.settings.UserOptions
import ua.com.programmer.agentventa.presentation.features.settings.UserOptionsBuilder
import ua.com.programmer.agentventa.presentation.features.settings.toJson
import ua.com.programmer.agentventa.utility.Constants
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * Implementation of SettingsSyncRepository using WebSocket.
 *
 * Handles bidirectional settings synchronization:
 * - Upload: Send current settings to server
 * - Download: Request and receive settings from server
 */
class SettingsSyncRepositoryImpl @Inject constructor(
    private val webSocketRepository: WebSocketRepository,
    private val gson: Gson,
    private val logger: Logger
) : SettingsSyncRepository {

    private val TAG = "SettingsSync"

    override suspend fun uploadSettings(
        userEmail: String,
        userAccount: ua.com.programmer.agentventa.data.local.entity.UserAccount,
        options: UserOptions
    ): Flow<SettingsSyncResult> = flow {
        android.util.Log.d(TAG, "=== UPLOAD SETTINGS START ===")
        android.util.Log.d(TAG, "Email: $userEmail")
        android.util.Log.d(TAG, "Device UUID: ${userAccount.guid}")
        android.util.Log.d(TAG, "Description: ${userAccount.description}")
        android.util.Log.d(TAG, "License: ${userAccount.license}")

        if (!webSocketRepository.isConnected()) {
            emit(SettingsSyncResult.Error("WebSocket not connected"))
            return@flow
        }

        logger.d(TAG, "Uploading settings for: $userEmail (device: ${userAccount.guid})")

        // Convert UserOptions to SettingsOptions
        val settingsOptions = options.toSettingsOptions()

        // Create settings data with full UserAccount information
        val settingsData = SettingsData(
            userEmail = userEmail,
            deviceUuid = userAccount.guid,
            description = userAccount.description,
            license = userAccount.license,
            dataFormat = userAccount.dataFormat,
            dbServer = userAccount.dbServer,
            dbName = userAccount.dbName,
            dbUser = userAccount.dbUser,
            dbPassword = userAccount.dbPassword,
            token = userAccount.token,
            relayServer = userAccount.relayServer,
            useWebSocket = userAccount.useWebSocket,
            options = settingsOptions
        )

        // Serialize to JSON
        val jsonData = gson.toJson(settingsData)
        logger.d(TAG, "Sending settings JSON (${jsonData.length} chars): ${jsonData.take(200)}...")
        android.util.Log.d(TAG, "JSON length: ${jsonData.length}")
        android.util.Log.d(TAG, "JSON preview: ${jsonData.take(500)}")
        if (jsonData.length > 500) {
            android.util.Log.d(TAG, "JSON continues: ${jsonData.substring(500, minOf(1000, jsonData.length))}")
        }

        // Send as data message with data_type: "settings"
        // Note: Backend currently queues settings for delivery instead of ACKing immediately
        // So we wait for the first Sent or Acknowledged result with a timeout
        val result = withTimeoutOrNull(5.seconds) {
            webSocketRepository.sendData(
                dataType = Constants.WEBSOCKET_DATA_TYPE_SETTINGS,
                data = jsonData
            ).first { result ->
                // Wait for first meaningful result (not Pending)
                result is SendResult.Sent ||
                result is SendResult.Acknowledged ||
                result is SendResult.Failed
            }
        }

        when (result) {
            is SendResult.Sent -> {
                logger.d(TAG, "Settings sent to server")
                emit(SettingsSyncResult.Success(settingsData))
            }
            is SendResult.Acknowledged -> {
                logger.d(TAG, "Settings upload acknowledged")
                emit(SettingsSyncResult.Success(settingsData))
            }
            is SendResult.Failed -> {
                logger.e(TAG, "Settings upload failed: ${result.error}")
                emit(SettingsSyncResult.Error(result.error))
            }
            is SendResult.Pending -> {
                logger.e(TAG, "Unexpected pending result after filtering")
                emit(SettingsSyncResult.Error("Unexpected pending state"))
            }
            null -> {
                logger.e(TAG, "Settings upload timeout")
                emit(SettingsSyncResult.Error("Upload timeout"))
            }
        }
    }.catch { e ->
        // Use Flow.catch operator to handle exceptions properly
        logger.e(TAG, "Error uploading settings: ${e.message}")
        emit(SettingsSyncResult.Error(e.message ?: "Unknown error"))
    }

    override suspend fun downloadSettings(
        userEmail: String
    ): Flow<SettingsSyncResult> = flow {
        if (!webSocketRepository.isConnected()) {
            emit(SettingsSyncResult.Error("WebSocket not connected"))
            return@flow
        }

        logger.d(TAG, "Requesting settings for: $userEmail")

        // Send sync_settings request
        val requestData = JsonObject().apply {
            addProperty("user_email", userEmail)
        }

        // Send the request and wait for first meaningful result
        val sendResult = withTimeoutOrNull(5.seconds) {
            webSocketRepository.sendMessage(
                type = Constants.WEBSOCKET_MESSAGE_TYPE_SYNC_SETTINGS,
                payload = gson.toJson(requestData)
            ).first { result ->
                // Wait for first meaningful result (not Pending)
                result is SendResult.Sent ||
                result is SendResult.Acknowledged ||
                result is SendResult.Failed
            }
        }

        when (sendResult) {
            is SendResult.Sent, is SendResult.Acknowledged -> {
                logger.d(TAG, "Settings request sent, waiting for response")

                // Wait for settings response from server
                val settings = withTimeoutOrNull(10.seconds) {
                    waitForSettingsResponse(userEmail)
                }

                if (settings != null) {
                    emit(settings)
                } else {
                    emit(SettingsSyncResult.Error("Timeout waiting for settings response"))
                }
            }
            is SendResult.Failed -> {
                logger.e(TAG, "Settings request failed: ${sendResult.error}")
                emit(SettingsSyncResult.Error(sendResult.error))
            }
            is SendResult.Pending -> {
                logger.e(TAG, "Unexpected pending result after filtering")
                emit(SettingsSyncResult.Error("Unexpected pending state"))
            }
            null -> {
                logger.e(TAG, "Settings request timeout")
                emit(SettingsSyncResult.Error("Request timeout"))
            }
        }
    }.catch { e ->
        // Use Flow.catch operator to handle exceptions properly
        logger.e(TAG, "Error downloading settings: ${e.message}")
        emit(SettingsSyncResult.Error(e.message ?: "Unknown error"))
    }

    /**
     * Wait for incoming settings message from server.
     * Filters incoming messages for settings data type.
     */
    private suspend fun waitForSettingsResponse(userEmail: String): SettingsSyncResult {
        return try {
            webSocketRepository.incomingMessages
                .timeout(10.seconds)
                .first { message ->
                    // Check if this is a settings message
                    message.dataType == Constants.WEBSOCKET_DATA_TYPE_SETTINGS
                }
                .let { message ->
                    parseSettingsMessage(message.data, userEmail)
                }
        } catch (e: Exception) {
            logger.e(TAG, "Error waiting for settings: ${e.message}")
            SettingsSyncResult.Error(e.message ?: "Error receiving settings")
        }
    }

    /**
     * Parse settings data from incoming message.
     */
    private fun parseSettingsMessage(data: JsonObject, userEmail: String): SettingsSyncResult {
        return try {
            val settingsData = gson.fromJson(data, SettingsData::class.java)

            if (settingsData.found == false) {
                logger.w(TAG, "Settings not found for: $userEmail")
                SettingsSyncResult.NotFound(userEmail)
            } else {
                logger.d(TAG, "Settings received for: $userEmail")
                SettingsSyncResult.Success(settingsData)
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error parsing settings: ${e.message}")
            SettingsSyncResult.Error("Failed to parse settings")
        }
    }
}

/**
 * Convert UserOptions to SettingsOptions for WebSocket sync.
 * Maps app's internal options structure to API format.
 */
private fun UserOptions.toSettingsOptions(): SettingsOptions {
    return SettingsOptions(
        write = this.write,
        read = this.read,
        loadImages = this.loadImages,
        useCompanies = this.useCompanies,
        useStores = this.useStores,
        clientsLocations = this.clientsLocations,
        fiscalProvider = this.fiscalProvider,
        fiscalProviderId = "", // Map from fiscalNumber if needed
        fiscalDeviceId = this.fiscalDeviceId,
        fiscalCashierPin = this.fiscalCashier,
        allowPriceTypeChoose = this.allowPriceTypeChoose,
        allowReturn = this.allowReturn,
        requireDeliveryDate = this.requireDeliveryDate,
        locations = this.locations,
        editLocations = this.editLocations,
        checkOrderLocation = this.checkOrderLocation,
        printingEnabled = this.printingEnabled,
        showClientPriceOnly = this.showClientPriceOnly,
        setClientPrice = this.setClientPrice,
        clientsDirections = this.clientsDirections,
        clientsProducts = this.clientsProducts,
        useDemands = this.useDemands,
        usePackageMark = this.usePackageMark,
        currency = this.currency,
        defaultClient = this.defaultClient
    )
}

/**
 * Convert SettingsOptions to UserOptions.
 * Maps API format back to app's internal structure.
 */
fun SettingsOptions.toUserOptions(
    existingOptions: UserOptions
): UserOptions {
    return existingOptions.copy(
        write = this.write,
        read = this.read,
        loadImages = this.loadImages,
        useCompanies = this.useCompanies,
        useStores = this.useStores,
        clientsLocations = this.clientsLocations,
        fiscalProvider = this.fiscalProvider,
        fiscalDeviceId = this.fiscalDeviceId,
        fiscalCashier = this.fiscalCashierPin,
        allowPriceTypeChoose = this.allowPriceTypeChoose,
        allowReturn = this.allowReturn,
        requireDeliveryDate = this.requireDeliveryDate,
        locations = this.locations,
        editLocations = this.editLocations,
        checkOrderLocation = this.checkOrderLocation,
        printingEnabled = this.printingEnabled,
        showClientPriceOnly = this.showClientPriceOnly,
        setClientPrice = this.setClientPrice,
        clientsDirections = this.clientsDirections,
        clientsProducts = this.clientsProducts,
        useDemands = this.useDemands,
        usePackageMark = this.usePackageMark,
        currency = this.currency,
        defaultClient = this.defaultClient
    )
}

/**
 * Convert SettingsData to UserAccount.
 * Applies downloaded settings to create an updated UserAccount.
 * Preserves the local GUID and isCurrent flag.
 * Uses null-safe handling for all fields to prevent NPE.
 * Only updates fields that are non-null in the server response.
 */
fun SettingsData.toUserAccount(
    currentAccount: ua.com.programmer.agentventa.data.local.entity.UserAccount
): ua.com.programmer.agentventa.data.local.entity.UserAccount {
    // Only update options if they're provided in the response
    val updatedOptionsJson = this.options?.let { serverOptions ->
        serverOptions.toUserOptions(
            ua.com.programmer.agentventa.presentation.features.settings.UserOptionsBuilder.build(currentAccount)
        ).toJson()
    } ?: currentAccount.options

    return currentAccount.copy(
        description = this.description ?: currentAccount.description,
        license = this.license ?: currentAccount.license,
        dataFormat = this.dataFormat ?: currentAccount.dataFormat,
        dbServer = this.dbServer ?: currentAccount.dbServer,
        dbName = this.dbName ?: currentAccount.dbName,
        dbUser = this.dbUser ?: currentAccount.dbUser,
        dbPassword = this.dbPassword ?: currentAccount.dbPassword,
        token = this.token ?: currentAccount.token,
        relayServer = this.relayServer ?: currentAccount.relayServer,
        useWebSocket = this.useWebSocket ?: currentAccount.useWebSocket,
        syncEmail = if (this.userEmail.isNotEmpty()) this.userEmail else currentAccount.syncEmail,
        options = updatedOptionsJson
    )
}
