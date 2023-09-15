package ua.com.programmer.agentventa.catalogs.locations.pickup

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.dao.entity.ClientLocation
import ua.com.programmer.agentventa.dao.entity.isValid
import ua.com.programmer.agentventa.geo.GeocodeHelper
import ua.com.programmer.agentventa.logger.Logger
import ua.com.programmer.agentventa.repository.ClientRepository
import javax.inject.Inject

@HiltViewModel
class LocationPickupViewModel @Inject constructor(
    private val repository: ClientRepository,
    private val geocode: GeocodeHelper,
    private val logger: Logger
) : ViewModel() {

    private val _clientLocation = MutableLiveData <ClientLocation>()
    val clientLocation get() = _clientLocation
    private val _address = MutableLiveData<String>()
    val address get() = _address

    var canEditLocation = false
        private set

    private var accountGuid = ""
    private var clientGuid = ""
    private var latitude = 0.0
    private var longitude = 0.0
    private var geocodedAddress = ""

    fun setAccountGuid(guid: String) {
        accountGuid = guid
    }

    fun setCanEditLocation(canEdit: Boolean) {
        canEditLocation = canEdit
    }

    // called when map is ready from OnMapReadyCallback
    fun setClientParameters(guid: String) {
        clientGuid = guid
        viewModelScope.launch {
            repository.getLocation(guid).collect { location ->
                _clientLocation.value = location
                _address.value = location.address
            }
        }
    }

    fun getAddress(lat: Double, lng: Double) {
        viewModelScope.launch {
            try {
                geocodedAddress = geocode.getAddress(lat, lng)
                _address.value = geocodedAddress
                latitude = lat
                longitude = lng
            } catch (e: Exception) {
                logger.w("Geocode", "get address: $e")
            }
        }
    }

    fun saveLocation(): Boolean {
        if (!canEditLocation) return false
        if (latitude == 0.0 && longitude == 0.0) return false
        val loadedLocation = clientLocation.value ?: ClientLocation(
            databaseId = accountGuid,
            clientGuid = clientGuid
        )
        val location = loadedLocation.copy(
            latitude = latitude,
            longitude = longitude,
            address = geocodedAddress,
            isModified = 1
        )
        if (!location.isValid()) return false
        viewModelScope.launch {
            repository.updateLocation(location)
        }
        return true
    }

//    fun onEditLocation() {
//        if (!canEditLocation) return
//    }

    fun onDeleteLocation(): Boolean {
        if (!canEditLocation) return false

        return true
    }

}