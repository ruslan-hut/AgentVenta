package ua.com.programmer.agentventa.repository

import ua.com.programmer.agentventa.dao.entity.LocationHistory
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun currentLocation(): Flow<LocationHistory?>
    suspend fun saveLocation(location: LocationHistory)
}