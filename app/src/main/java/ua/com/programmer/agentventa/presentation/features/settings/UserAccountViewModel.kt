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
import ua.com.programmer.agentventa.utility.Constants
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class UserAccountViewModel @Inject constructor(
    private val userAccountRepository: UserAccountRepository
) : ViewModel() {

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
        selectedFormat.value = Constants.SYNC_FORMAT_HTTP
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
}