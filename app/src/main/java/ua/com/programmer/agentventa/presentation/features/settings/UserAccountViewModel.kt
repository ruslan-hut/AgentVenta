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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.com.programmer.agentventa.data.local.entity.UserAccount
import ua.com.programmer.agentventa.data.repository.toUserAccount
import ua.com.programmer.agentventa.data.repository.toUserOptions
import ua.com.programmer.agentventa.data.websocket.SettingsSyncResult
import ua.com.programmer.agentventa.data.websocket.WebSocketState
import ua.com.programmer.agentventa.domain.repository.SettingsSyncRepository
import ua.com.programmer.agentventa.domain.repository.UserAccountRepository
import ua.com.programmer.agentventa.domain.repository.WebSocketRepository
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import ua.com.programmer.agentventa.utility.Constants
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class UserAccountViewModel @Inject constructor(
    private val userAccountRepository: UserAccountRepository,
    private val webSocketRepository: WebSocketRepository,
    private val settingsSyncRepository: SettingsSyncRepository,
    private val logger: Logger
) : ViewModel() {

    private val TAG = "UserAccountVM"

    private val _guid = MutableLiveData("")
    val formatSpinner = MutableLiveData<List<String>>()
    val selectedFormat = MutableLiveData<String>()

    private val _connectionState = MutableLiveData<String>("✗ Disconnected")
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
                    is WebSocketState.Connected -> "✓ Connected (UUID: ${state.deviceUuid.take(8)})"
                    is WebSocketState.Connecting -> "⟳ Connecting... (attempt ${state.attempt})"
                    is WebSocketState.Disconnected -> "✗ Disconnected"
                    is WebSocketState.Pending -> "⏸ Pending Approval (UUID: ${state.deviceUuid.take(8)})"
                    is WebSocketState.Error -> "✗ Error: ${state.error}"
                    is WebSocketState.Reconnecting -> "⟳ Reconnecting in ${state.delayMs}ms (attempt ${state.attempt})"
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
                _connectionState.value = "✗ Error: No account loaded"
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
                _settingsSyncStatus.value = "❌ No account loaded"
                return@launch
            }

            // Get user email from account syncEmail field
            val userEmail = currentAccount.syncEmail
            if (userEmail.isBlank()) {
                _settingsSyncStatus.value = "❌ Email required for sync"
                return@launch
            }

            _settingsSyncStatus.value = "⟳ Uploading settings..."
            logger.d(TAG, "Uploading full account settings for: $userEmail")

            val options = UserOptionsBuilder.build(currentAccount)

            settingsSyncRepository.uploadSettings(userEmail, currentAccount, options).collect { result ->
                when (result) {
                    is SettingsSyncResult.Success -> {
                        _settingsSyncStatus.value = "✓ Settings uploaded"
                        logger.d(TAG, "Settings uploaded successfully")
                    }
                    is SettingsSyncResult.Error -> {
                        _settingsSyncStatus.value = "❌ Upload failed: ${result.message}"
                        logger.e(TAG, "Upload error: ${result.message}")
                    }
                    is SettingsSyncResult.NotFound -> {
                        _settingsSyncStatus.value = "❌ Unexpected result"
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
                _settingsSyncStatus.value = "❌ No account loaded"
                return@launch
            }

            // Get user email from account syncEmail field
            val userEmail = currentAccount.syncEmail
            if (userEmail.isBlank()) {
                _settingsSyncStatus.value = "❌ Email required for sync"
                return@launch
            }

            _settingsSyncStatus.value = "⟳ Downloading settings..."
            logger.d(TAG, "Downloading settings for: $userEmail")

            settingsSyncRepository.downloadSettings(userEmail).collect { result ->
                when (result) {
                    is SettingsSyncResult.Success -> {
                        _settingsSyncStatus.value = "✓ Settings downloaded"
                        logger.d(TAG, "Settings received - applying full account data")

                        // Apply downloaded settings to create updated UserAccount
                        val updatedAccount = result.settings.toUserAccount(currentAccount)

                        // Save updated account
                        userAccountRepository.saveAccount(updatedAccount)
                        _settingsSyncStatus.value = "✓ Settings applied"
                        logger.d(TAG, "Full account settings applied")

                        // If useWebSocket is enabled, automatically upload current version back to server
                        if (updatedAccount.useWebSocket && updatedAccount.syncEmail.isNotBlank()) {
                            logger.d(TAG, "Auto-uploading current settings after download")
                            uploadSettings(updatedAccount)
                        }
                    }
                    is SettingsSyncResult.NotFound -> {
                        _settingsSyncStatus.value = "ℹ No settings found on server"
                        logger.d(TAG, "No settings found for: $userEmail")
                    }
                    is SettingsSyncResult.Error -> {
                        _settingsSyncStatus.value = "❌ Download failed: ${result.message}"
                        logger.e(TAG, "Download error: ${result.message}")
                    }
                }
            }
        }
    }
}