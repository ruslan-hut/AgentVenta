package ua.com.programmer.agentventa.catalogs.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.dao.entity.ClientImage
import ua.com.programmer.agentventa.dao.entity.Debt
import ua.com.programmer.agentventa.dao.entity.LClient
import ua.com.programmer.agentventa.repository.ClientRepository
import ua.com.programmer.agentventa.repository.FilesRepository
import ua.com.programmer.agentventa.shared.SharedParameters
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ClientViewModel @Inject constructor(
    private val clientRepository: ClientRepository,
    private val filesRepository: FilesRepository
): ViewModel() {

    private val _clientGuid = MutableStateFlow("")
    private val _params = MutableStateFlow(SharedParameters())

    // Client as StateFlow
    private val _clientFlow: StateFlow<LClient?> = combine(
        _clientGuid,
        _params
    ) { guid, params ->
        Pair(guid, params.companyGuid)
    }.flatMapLatest { (guid, companyGuid) ->
        if (guid.isEmpty()) flowOf(null)
        else clientRepository.getClient(guid, companyGuid)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )
    val clientFlow: StateFlow<LClient?> = _clientFlow
    val client = _clientFlow.asLiveData()

    // Debt list as StateFlow
    private val _debtListFlow: StateFlow<List<Debt>> = combine(
        _clientGuid,
        _params
    ) { guid, params ->
        Pair(guid, params.companyGuid)
    }.flatMapLatest { (guid, companyGuid) ->
        if (guid.isEmpty()) flowOf(emptyList())
        else clientRepository.getDebts(guid, companyGuid)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    val debtListFlow: StateFlow<List<Debt>> = _debtListFlow
    val debtList = _debtListFlow.asLiveData()

    // Client images as StateFlow
    private val _clientImagesFlow: StateFlow<List<ClientImage>> = _clientGuid
        .flatMapLatest { guid ->
            if (guid.isEmpty()) flowOf(emptyList())
            else filesRepository.getClientImages(guid)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    val clientImagesFlow: StateFlow<List<ClientImage>> = _clientImagesFlow
    val clientImages = _clientImagesFlow.asLiveData()

    val paramsFlow: StateFlow<SharedParameters> = _params.asStateFlow()

    fun setParameters(parameters: SharedParameters) {
        _params.value = parameters
    }

    fun setDefaultImage(image: ClientImage) {
        viewModelScope.launch {
            filesRepository.setAsDefault(image)
        }
    }

    fun setClientParameters(guid: String) {
        _clientGuid.value = guid
    }
}
