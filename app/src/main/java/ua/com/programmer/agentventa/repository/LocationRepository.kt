package ua.com.programmer.agentventa.repository

import ua.com.programmer.agentventa.dao.entity.LocationHistory

interface LocationRepository {
    suspend fun saveLocation(location: LocationHistory)
}