package ua.com.programmer.agentventa.presentation.features.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.data.local.entity.UserAccount
import ua.com.programmer.agentventa.domain.repository.UserAccountRepository
import javax.inject.Inject

@HiltViewModel
class UserAccountListViewModel @Inject constructor(
    private val userAccountRepository: UserAccountRepository
) : ViewModel() {

    val accounts: LiveData<List<UserAccount>> = userAccountRepository.getAll().asLiveData()

    private val _alertMessage = MutableLiveData(0)
    val alertMessage: LiveData<Int> = _alertMessage

    fun setAccountAsCurrent(account: UserAccount) {
        viewModelScope.launch(Dispatchers.IO) {
            userAccountRepository.setIsCurrent(account.guid)
        }
    }

    fun checkCurrentAccount(afterCheck: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = userAccountRepository.getCurrent()
            withContext(Dispatchers.Main) {
                if (current == null) {
                    _alertMessage.postValue(R.string.error_no_current_account)
                } else {
                    _alertMessage.postValue(0)
                    afterCheck()
                }
            }
        }
    }

}