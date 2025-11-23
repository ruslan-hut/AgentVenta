package ua.com.programmer.agentventa.presentation.features.map.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import ua.com.programmer.agentventa.domain.repository.ClientRepository
import ua.com.programmer.agentventa.domain.repository.LocationRepository
import javax.inject.Inject

@HiltViewModel
class ClientsMapViewModel@Inject constructor(
    repository: ClientRepository,
    locationRepository: LocationRepository,
) : ViewModel() {

    val locations = repository.getLocations().asLiveData()
    val currentLocation = locationRepository.currentLocation().asLiveData()

}