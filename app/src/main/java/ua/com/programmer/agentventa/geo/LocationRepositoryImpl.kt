package ua.com.programmer.agentventa.geo

import ua.com.programmer.agentventa.dao.LocationDao
import ua.com.programmer.agentventa.dao.entity.LocationHistory
import ua.com.programmer.agentventa.repository.LocationRepository
import javax.inject.Inject

class LocationRepositoryImpl @Inject constructor(private val dao: LocationDao): LocationRepository {
    override suspend fun saveLocation(location: LocationHistory) {
        dao.insertLocation(location)
    }
}