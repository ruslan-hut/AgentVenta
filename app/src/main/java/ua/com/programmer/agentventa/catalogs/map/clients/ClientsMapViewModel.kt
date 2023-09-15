package ua.com.programmer.agentventa.catalogs.map.clients

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.dao.entity.ClientLocation
import ua.com.programmer.agentventa.repository.LocationRepository
import javax.inject.Inject

@HiltViewModel
class ClientsMapViewModel@Inject constructor(
    private val repository: LocationRepository
) : ViewModel() {
    private val _clientsLocation = MutableLiveData <List<ClientLocation>>()
    val clientsLocation get() = _clientsLocation

    fun setMapParameters() {
        viewModelScope.launch {
//            repository.getClientsLocation().collect { location ->
//                _clientsLocation.value = location
//                Log.d("ClientsMapViewModel", "clientsLocation: $location")
//            }
        }
    }

}