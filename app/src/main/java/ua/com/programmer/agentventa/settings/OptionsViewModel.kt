package ua.com.programmer.agentventa.settings

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.dao.entity.UserAccount
import ua.com.programmer.agentventa.repository.UserAccountRepository
import javax.inject.Inject

@HiltViewModel
class OptionsViewModel @Inject constructor(
    private val repository: UserAccountRepository
): ViewModel() {

    private val _currentAccount = MutableLiveData<UserAccount>()
    val currentAccount get() = _currentAccount
    private var _options = MutableLiveData<UserOptions>()
    val options get() = _options

    init {
        viewModelScope.launch {
            repository.currentAccount.collect {
                _currentAccount.value = it
                _options.value = UserOptionsBuilder.build(it)
            }
        }
    }
}