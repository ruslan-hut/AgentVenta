package ua.com.programmer.agentventa.presentation.features.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.data.local.entity.UserAccount
import ua.com.programmer.agentventa.data.repository.toUserAccount
import ua.com.programmer.agentventa.data.websocket.SettingsSyncResult
import ua.com.programmer.agentventa.data.websocket.WebSocketState
import ua.com.programmer.agentventa.domain.repository.SettingsSyncRepository
import ua.com.programmer.agentventa.domain.repository.UserAccountRepository
import ua.com.programmer.agentventa.domain.repository.WebSocketRepository
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import ua.com.programmer.agentventa.utility.Constants
import ua.com.programmer.agentventa.utility.ResourceProvider
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class UserAccountViewModel @Inject constructor(
    private val resourceProvider: ResourceProvider,
    private val userAccountRepository: UserAccountRepository,
    private val webSocketRepository: WebSocketRepository,
    private val settingsSyncRepository: SettingsSyncRepository,
    private val logger: Logger
) : ViewModel() {

    private val TAG = "UserAccountVM"

    private val _guid = MutableLiveData("")
    val formatSpinner = MutableLiveData<List<String>>()
    val selectedFormat = MutableLiveData<String>()

    private val _connectionState = MutableLiveData(resourceProvider.getString(R.string.account_ws_disconnected))
    val connectionState: LiveData<String> = _connectionState

    private val _isConnected = MutableLiveData<Boolean>(false)
    val isConnected: LiveData<Boolean> = _isConnected

    private val _isConnecting = MutableLiveData<Boolean>(false)
    val isConnecting: LiveData<Boolean> = _isConnecting

    private val _settingsSyncStatus = MutableLiveData<String>("")
    val settingsSyncStatus: LiveData<String> = _settingsSyncStatus

    val account get() = _guid.switchMap {
        userAccountRepository.getByGuid(it).asLiveData()
    }

    init {
        formatSpinner.value = listOf(
            Constants.SYNC_FORMAT_HTTP,
            Constants.SYNC_FORMAT_FTP,
            Constants.SYNC_FORMAT_WEBSOCKET
        )
        selectedFormat.value = Constants.SYNC_FORMAT_WEBSOCKET // Default to relay/WebSocket

        // Observe WebSocket connection state
        webSocketRepository.connectionState
            .onEach { state ->
                // Update status text
                val statusText = when (state) {
                    is WebSocketState.Connected -> resourceProvider.getString(R.string.account_ws_connected, state.deviceUuid.take(8))
                    is WebSocketState.Connecting -> resourceProvider.getString(R.string.account_ws_connecting, state.attempt)
                    is WebSocketState.Disconnected -> resourceProvider.getString(R.string.account_ws_disconnected)
                    is WebSocketState.Pending -> resourceProvider.getString(R.string.account_ws_pending, state.deviceUuid.take(8))
                    is WebSocketState.Error -> resourceProvider.getString(R.string.account_ws_error, state.error)
                    is WebSocketState.Reconnecting -> resourceProvider.getString(R.string.account_ws_reconnecting, state.delayMs, state.attempt)
                }
                _connectionState.postValue(statusText)

                // Update boolean states for UI logic
                _isConnected.postValue(state is WebSocketState.Connected)
                _isConnecting.postValue(
                    state is WebSocketState.Connecting || state is WebSocketState.Reconnecting
                )
            }
            .launchIn(viewModelScope)
    }

    fun setCurrentAccount(guid: String?) {
        if (guid.isNullOrBlank()) {
            _guid.value = UUID.randomUUID().toString()
        }else{
            _guid.value = guid
        }
    }

    fun saveAccount(updated: UserAccount, afterSave: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            userAccountRepository.saveAccount(updated)

            // If WebSocket is enabled, automatically upload settings to server
            if (updated.useWebSocket && updated.syncEmail.isNotBlank()) {
                logger.d(TAG, "Auto-uploading settings after save (useWebSocket=true)")
                withContext(Dispatchers.Main) {
                    uploadSettings(updated)
                }
            }

            withContext(Dispatchers.Main) {
                afterSave()
            }
        }
    }

    fun deleteAccount(guid: String, afterDelete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (userAccountRepository.deleteByGuid(guid) > 0) {
                withContext(Dispatchers.Main) {
                    afterDelete()
                }
            }
        }
    }

    // WebSocket connection methods
    fun connectWebSocket(userAccount: UserAccount? = null) {
        viewModelScope.launch {
            val currentAccount = userAccount ?: account.value
            if (currentAccount != null) {
                logger.d(TAG, "Connecting WebSocket for account: ${currentAccount.description}")
                webSocketRepository.connect(currentAccount)
            } else {
                logger.w(TAG, "Cannot connect: No account loaded")
                _connectionState.value = resourceProvider.getString(R.string.account_ws_no_account)
            }
        }
    }

    fun disconnectWebSocket() {
        viewModelScope.launch {
            logger.d(TAG, "Disconnecting WebSocket")
            webSocketRepository.disconnect()
        }
    }

    // Settings sync methods
    fun uploadSettings(userAccount: UserAccount? = null) {
        viewModelScope.launch {
            val currentAccount = userAccount ?: account.value
            if (currentAccount == null) {
                _settingsSyncStatus.value = resourceProvider.getString(R.string.settings_sync_no_account)
                return@launch
            }

            // Get user email from account syncEmail field
            val userEmail = currentAccount.syncEmail
            if (userEmail.isBlank()) {
                _settingsSyncStatus.value = resourceProvider.getString(R.string.settings_sync_email_required)
                return@launch
            }

            _settingsSyncStatus.value = resourceProvider.getString(R.string.settings_sync_uploading)
            logger.d(TAG, "Uploading full account settings for: $userEmail")

            val options = UserOptionsBuilder.build(currentAccount)

            settingsSyncRepository.uploadSettings(userEmail, currentAccount, options).collect { result ->
                when (result) {
                    is SettingsSyncResult.Success -> {
                        _settingsSyncStatus.value = resourceProvider.getString(R.string.settings_sync_uploaded)
                        logger.d(TAG, "Settings uploaded successfully")
                    }
                    is SettingsSyncResult.Error -> {
                        _settingsSyncStatus.value = resourceProvider.getString(R.string.settings_sync_upload_failed, result.message)
                        logger.e(TAG, "Upload error: ${result.message}")
                    }
                    is SettingsSyncResult.NotFound -> {
                        _settingsSyncStatus.value = resourceProvider.getString(R.string.settings_sync_unexpected)
                        logger.w(TAG, "Unexpected NotFound on upload")
                    }
                }
            }
        }
    }

    fun downloadSettings(userAccount: UserAccount? = null) {
        viewModelScope.launch {
            val currentAccount = userAccount ?: account.value
            if (currentAccount == null) {
                _settingsSyncStatus.value = resourceProvider.getString(R.string.settings_sync_no_account)
                return@launch
            }

            // Get user email from account syncEmail field
            val userEmail = currentAccount.syncEmail
            if (userEmail.isBlank()) {
                _settingsSyncStatus.value = resourceProvider.getString(R.string.settings_sync_email_required)
                return@launch
            }

            _settingsSyncStatus.value = resourceProvider.getString(R.string.settings_sync_downloading)
            logger.d(TAG, "Downloading settings for: $userEmail")

            settingsSyncRepository.downloadSettings(userEmail).collect { result ->
                when (result) {
                    is SettingsSyncResult.Success -> {
                        _settingsSyncStatus.value = resourceProvider.getString(R.string.settings_sync_downloaded)
                        logger.d(TAG, "Settings received - applying full account data")

                        // Apply downloaded settings to create updated UserAccount
                        val updatedAccount = result.settings.toUserAccount(currentAccount)

                        // Save updated account
                        userAccountRepository.saveAccount(updatedAccount)
                        _settingsSyncStatus.value = resourceProvider.getString(R.string.settings_sync_applied)
                        logger.d(TAG, "Full account settings applied")

                        // If useWebSocket is enabled, automatically upload current version back to server
                        if (updatedAccount.useWebSocket && updatedAccount.syncEmail.isNotBlank()) {
                            logger.d(TAG, "Auto-uploading current settings after download")
                            uploadSettings(updatedAccount)
                        }
                    }
                    is SettingsSyncResult.NotFound -> {
                        _settingsSyncStatus.value = resourceProvider.getString(R.string.settings_sync_not_found)
                        logger.d(TAG, "No settings found for: $userEmail")
                    }
                    is SettingsSyncResult.Error -> {
                        _settingsSyncStatus.value = resourceProvider.getString(R.string.settings_sync_download_failed, result.message)
                        logger.e(TAG, "Download error: ${result.message}")
                    }
                }
            }
        }
    }
}