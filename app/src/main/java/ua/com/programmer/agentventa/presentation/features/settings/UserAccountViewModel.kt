package ua.com.programmer.agentventa.presentation.features.settings

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.com.programmer.agentventa.data.local.entity.UserAccount
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
    private val logger: Logger
) : ViewModel() {

    private val TAG = "AV-UserAccountVM"

    private val _guid = MutableLiveData("")
    val formatSpinner = MutableLiveData<List<String>>()
    val selectedFormat = MutableLiveData<String>()

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
    }

    fun setCurrentAccount(guid: String?) {
        if (guid.isNullOrBlank()) {
            _guid.value = UUID.randomUUID().toString()
        }else{
            _guid.value = guid
        }
    }

    fun saveAccount(updated: UserAccount, onComplete: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { userAccountRepository.saveAccount(updated) }
            onComplete()
        }
    }

    fun deleteAccount(guid: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) { userAccountRepository.deleteByGuid(guid) }
            if (deleted > 0) onComplete()
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
            }
        }
    }

    fun disconnectWebSocket() {
        viewModelScope.launch {
            logger.d(TAG, "Disconnecting WebSocket")
            webSocketRepository.disconnect()
        }
    }

}