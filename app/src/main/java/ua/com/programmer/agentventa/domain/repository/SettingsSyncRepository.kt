package ua.com.programmer.agentventa.domain.repository

import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.data.websocket.SettingsSyncResult
import ua.com.programmer.agentventa.presentation.features.settings.UserOptions

/**
 * Repository for synchronizing settings via WebSocket.
 *
 * Settings sync allows users to backup and restore their app configuration
 * across multiple devices or after reinstalling the app.
 */
interface SettingsSyncRepository {

    /**
     * Upload current settings to the server.
     *
     * @param userEmail User's email address (identifier for settings)
     * @param options Current user options to upload
     * @return Flow emitting the upload result
     */
    suspend fun uploadSettings(
        userEmail: String,
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
