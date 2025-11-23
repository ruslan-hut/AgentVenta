package ua.com.programmer.agentventa.presentation.features.map.history

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.data.local.entity.ClientLocation
import ua.com.programmer.agentventa.domain.repository.LocationRepository
import javax.inject.Inject

@HiltViewModel
class LocationHistoryViewModel@Inject constructor(
    private val repository: LocationRepository
) : ViewModel() {
    private val _clientHistory = MutableLiveData <List<ClientLocation>>()
    val clientHistory get() = _clientHistory

    private var clientGuid = ""

    fun setMapParameters(guid: String) {
        clientGuid = guid
        viewModelScope.launch {
//            repository.getClientHistory(guid).collect { location ->
//                _clientsLocation.value = location
//                Log.d("ClientsMapViewModel", "clientsLocation: $location")
//            }
        }
    }

}