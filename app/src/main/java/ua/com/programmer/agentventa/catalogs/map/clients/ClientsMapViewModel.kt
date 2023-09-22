package ua.com.programmer.agentventa.catalogs.map.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import ua.com.programmer.agentventa.repository.ClientRepository
import ua.com.programmer.agentventa.repository.LocationRepository
import javax.inject.Inject

@HiltViewModel
class ClientsMapViewModel@Inject constructor(
    repository: ClientRepository,
    locationRepository: LocationRepository,
) : ViewModel() {

    val locations = repository.getLocations().asLiveData()
    val currentLocation = locationRepository.currentLocation().asLiveData()

}