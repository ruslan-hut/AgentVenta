package ua.com.programmer.agentventa.domain.repository

import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.data.local.entity.UserAccount
import ua.com.programmer.agentventa.data.websocket.SettingsSyncResult
import ua.com.programmer.agentventa.presentation.features.settings.UserOptions

/**
 * Repository for synchronizing settings via WebSocket.
 *
 * Settings sync allows users to backup and restore their full account configuration
 * across multiple devices or after reinstalling the app.
 */
interface SettingsSyncRepository {

    /**
     * Upload current account settings to the server.
     *
     * @param userEmail User's email address (identifier for settings)
     * @param userAccount Full UserAccount data to upload
     * @param options Current user options to upload
     * @return Flow emitting the upload result
     */
    suspend fun uploadSettings(
        userEmail: String,
        userAccount: UserAccount,
        options: UserOptions
    ): Flow<SettingsSyncResult>

    /**
     * Download settings from the server.
     *
     * @param userEmail User's email address (identifier for settings)
     * @return Flow emitting the download result
     */
    suspend fun downloadSettings(
        userEmail: String
    ): Flow<SettingsSyncResult>
}
