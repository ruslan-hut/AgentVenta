package ua.com.programmer.agentventa.catalogs.client

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.dao.entity.ClientImage
import ua.com.programmer.agentventa.dao.entity.LClient
import ua.com.programmer.agentventa.repository.ClientRepository
import ua.com.programmer.agentventa.repository.FilesRepository
import ua.com.programmer.agentventa.shared.SharedParameters
import javax.inject.Inject

@HiltViewModel
class ClientViewModel @Inject constructor(
    private val clientRepository: ClientRepository,
    private val filesRepository: FilesRepository
): ViewModel() {

    private val _client = MediatorLiveData <LClient>()
    val client get() = _client

    private val params = MutableLiveData<SharedParameters>()
    private val clientGuid = MutableLiveData("")

    private val guid get() = clientGuid.value ?: ""
    private val companyGuid get() = params.value?.companyGuid ?: ""

    val debtList = _client.switchMap { client ->
        clientRepository.getDebts(guid, companyGuid).asLiveData()
    }

    val clientImages = _client.switchMap { client ->
        filesRepository.getClientImages(client.guid).asLiveData()
    }

    fun setParameters(parameters: SharedParameters) {
        params.value = parameters
    }

    fun setDefaultImage(image: ClientImage) {
        viewModelScope.launch {
            filesRepository.setAsDefault(image)
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            clientRepository.getClient(guid, companyGuid).collect { clientInfo ->
                _client.value = clientInfo
            }
        }
    }

    fun setClientParameters(guid: String) {
        clientGuid.value = guid
    }

    init {
        _client.addSource(clientGuid) { loadData() }
        _client.addSource(params) { loadData() }
    }

}