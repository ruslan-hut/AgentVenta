package ua.com.programmer.agentventa.data.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.withTimeoutOrNull
import ua.com.programmer.agentventa.data.websocket.*
import ua.com.programmer.agentventa.domain.repository.SettingsSyncRepository
import ua.com.programmer.agentventa.domain.repository.WebSocketRepository
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import ua.com.programmer.agentventa.presentation.features.settings.UserOptions
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
        options: UserOptions
    ): Flow<SettingsSyncResult> = flow {
        if (!webSocketRepository.isConnected()) {
            emit(SettingsSyncResult.Error("WebSocket not connected"))
            return@flow
        }

        try {
            logger.d(TAG, "Uploading settings for: $userEmail")

            // Convert UserOptions to SettingsOptions
            val settingsOptions = options.toSettingsOptions()

            // Create settings data
            val settingsData = SettingsData(
                userEmail = userEmail,
                options = settingsOptions
            )

            // Send as data message with data_type: "settings"
            val result = webSocketRepository.sendData(
                dataType = Constants.WEBSOCKET_DATA_TYPE_SETTINGS,
                data = gson.toJson(settingsData)
            ).first()

            when (result) {
                is SendResult.Acknowledged -> {
                    logger.d(TAG, "Settings uploaded successfully")
                    emit(SettingsSyncResult.Success(settingsData))
                }
                is SendResult.Failed -> {
                    logger.e(TAG, "Settings upload failed: ${result.error}")
                    emit(SettingsSyncResult.Error(result.error))
                }
                else -> {
                    // Sent but not yet acknowledged
                    logger.d(TAG, "Settings sent, waiting for ACK")
                }
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error uploading settings: ${e.message}")
            emit(SettingsSyncResult.Error(e.message ?: "Unknown error"))
        }
    }

    override suspend fun downloadSettings(
        userEmail: String
    ): Flow<SettingsSyncResult> = flow {
        if (!webSocketRepository.isConnected()) {
            emit(SettingsSyncResult.Error("WebSocket not connected"))
            return@flow
        }

        try {
            logger.d(TAG, "Requesting settings for: $userEmail")

            // Send sync_settings request
            val requestData = JsonObject().apply {
                addProperty("user_email", userEmail)
            }

            val result = webSocketRepository.sendMessage(
                type = Constants.WEBSOCKET_MESSAGE_TYPE_SYNC_SETTINGS,
                payload = gson.toJson(requestData)
            ).first()

            when (result) {
                is SendResult.Acknowledged -> {
                    logger.d(TAG, "Settings request sent, waiting for response")

                    // Wait for incoming settings message with timeout
                    val settings = withTimeoutOrNull(10.seconds) {
                        waitForSettingsResponse(userEmail)
                    }

                    if (settings != null) {
                        emit(settings)
                    } else {
                        emit(SettingsSyncResult.Error("Timeout waiting for settings"))
                    }
                }
                is SendResult.Failed -> {
                    logger.e(TAG, "Settings request failed: ${result.error}")
                    emit(SettingsSyncResult.Error(result.error))
                }
                else -> {
                    logger.d(TAG, "Settings request sent")
                }
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error downloading settings: ${e.message}")
            emit(SettingsSyncResult.Error(e.message ?: "Unknown error"))
        }
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
