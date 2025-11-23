package ua.com.programmer.agentventa.infrastructure.location

import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.data.local.dao.LocationDao
import ua.com.programmer.agentventa.data.local.entity.LocationHistory
import ua.com.programmer.agentventa.domain.repository.LocationRepository
import javax.inject.Inject

class LocationRepositoryImpl @Inject constructor(private val dao: LocationDao): LocationRepository {

    override fun currentLocation(): Flow<LocationHistory?> {
        return dao.currentLocation()
    }

    override suspend fun saveLocation(location: LocationHistory) {
        dao.insertLocation(location)
    }
}