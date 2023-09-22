package ua.com.programmer.agentventa.repository

import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.dao.entity.ClientLocation
import ua.com.programmer.agentventa.dao.entity.Debt
import ua.com.programmer.agentventa.dao.entity.LClient
import ua.com.programmer.agentventa.dao.entity.LClientLocation

interface ClientRepository {
    fun getClient(guid: String): Flow<LClient>
    fun getClients(group: String, filter: String): Flow<List<LClient>>
    fun getDebts(guid: String): Flow<List<Debt>>
    fun getDebt(guid: String, docId: String): Flow<Debt>
    fun getLocation(guid: String): Flow<LClientLocation>
    fun getLocations(): Flow<List<LClientLocation>>
    suspend fun updateLocation(location: ClientLocation)
    suspend fun deleteLocation(location: ClientLocation)
}