package ua.com.programmer.agentventa.domain.repository

import ua.com.programmer.agentventa.data.local.entity.LocationHistory
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun currentLocation(): Flow<LocationHistory?>
    suspend fun saveLocation(location: LocationHistory)
}