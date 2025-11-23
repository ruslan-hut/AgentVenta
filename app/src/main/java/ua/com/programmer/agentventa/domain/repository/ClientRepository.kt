package ua.com.programmer.agentventa.domain.repository

import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.data.local.entity.ClientLocation
import ua.com.programmer.agentventa.data.local.entity.Debt
import ua.com.programmer.agentventa.data.local.entity.LClient
import ua.com.programmer.agentventa.data.local.entity.LClientLocation

interface ClientRepository {
    fun getClient(guid: String, companyGuid: String): Flow<LClient>
    fun getClients(group: String, filter: String, companyGuid: String): Flow<List<LClient>>
    fun getDebts(guid: String, companyGuid: String): Flow<List<Debt>>
    fun getDebt(guid: String, docId: String): Flow<Debt>
    fun getLocation(guid: String): Flow<LClientLocation>
    fun getLocations(): Flow<List<LClientLocation>>
    suspend fun updateLocation(location: ClientLocation)
    suspend fun deleteLocation(location: ClientLocation)
}