package ua.com.programmer.agentventa.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ua.com.programmer.agentventa.dao.entity.UserAccount
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(): ViewModel() {

    var isUpdated = false
    var account: UserAccount? = null

}