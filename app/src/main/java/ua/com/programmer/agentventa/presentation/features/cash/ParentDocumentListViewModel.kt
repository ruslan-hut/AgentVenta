package ua.com.programmer.agentventa.presentation.features.cash

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import ua.com.programmer.agentventa.domain.repository.ClientRepository
import ua.com.programmer.agentventa.presentation.features.client.DebtListItem
import ua.com.programmer.agentventa.presentation.features.client.withGroupHeaders
import javax.inject.Inject

@HiltViewModel
class ParentDocumentListViewModel @Inject constructor(
    clientRepository: ClientRepository,
    savedStateHandle: SavedStateHandle,
): ViewModel() {

    private val clientGuid: String = savedStateHandle["clientGuid"] ?: ""
    private val companyGuid: String = savedStateHandle["companyGuid"] ?: ""

    val debtListItems = clientRepository.getSelectableDebts(clientGuid, companyGuid)
        .map { it.withGroupHeaders() }
        .asLiveData()
}
