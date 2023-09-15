package ua.com.programmer.agentventa.catalogs.client

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
import javax.inject.Inject

@HiltViewModel
class ClientViewModel @Inject constructor(
    private val clientRepository: ClientRepository,
    private val filesRepository: FilesRepository
): ViewModel() {

    private val _client = MutableLiveData <LClient>()
    val client get() = _client

    val debtList = _client.switchMap { client ->
        clientRepository.getDebts(client.guid).asLiveData()
    }

    val clientImages = _client.switchMap { client ->
        filesRepository.getClientImages(client.guid).asLiveData()
    }

    fun setDefaultImage(image: ClientImage) {
        viewModelScope.launch {
            filesRepository.setAsDefault(image)
        }
    }

    fun setClientParameters(guid: String) {
        viewModelScope.launch {
            clientRepository.getClient(guid).collect { clientInfo ->
                _client.value = clientInfo
            }
        }
    }

}