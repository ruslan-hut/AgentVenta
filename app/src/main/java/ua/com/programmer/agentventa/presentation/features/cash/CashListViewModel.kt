package ua.com.programmer.agentventa.presentation.features.cash

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.data.local.entity.Cash
import ua.com.programmer.agentventa.presentation.common.document.DocumentListViewModel
import ua.com.programmer.agentventa.domain.repository.CashRepository
import ua.com.programmer.agentventa.domain.repository.UserAccountRepository
import javax.inject.Inject

@HiltViewModel
class CashListViewModel @Inject constructor(
    private val cashRepository: CashRepository,
    userAccountRepository: UserAccountRepository
): DocumentListViewModel<Cash>(cashRepository, userAccountRepository) {

    init {
        setTotalsVisible(true)
    }

    fun markReadyToSend(document: Cash, onResult: () -> Unit) {
        viewModelScope.launch {
            val updated = document.copy(isSent = 0, isProcessed = 1)
            cashRepository.updateDocument(updated)
            onResult()
        }
    }

}