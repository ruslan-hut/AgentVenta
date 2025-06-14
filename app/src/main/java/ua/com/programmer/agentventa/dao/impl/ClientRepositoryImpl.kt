package ua.com.programmer.agentventa.dao.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ua.com.programmer.agentventa.dao.ClientDao
import ua.com.programmer.agentventa.dao.DataExchangeDao
import ua.com.programmer.agentventa.dao.entity.ClientLocation
import ua.com.programmer.agentventa.dao.entity.Debt
import ua.com.programmer.agentventa.dao.entity.LClient
import ua.com.programmer.agentventa.dao.entity.LClientLocation
import ua.com.programmer.agentventa.extensions.asFilter
import ua.com.programmer.agentventa.repository.ClientRepository
import javax.inject.Inject

class ClientRepositoryImpl @Inject constructor(
    private val dao: ClientDao,
    private val updateDao: DataExchangeDao
): ClientRepository {

    override fun getClient(guid: String, companyGuid: String): Flow<LClient> {
        return dao.getClientInfo(guid, companyGuid).map {
            it ?: LClient()
        }
    }

    override fun getClients(group: String, filter: String, companyGuid: String): Flow<List<LClient>> {
        return dao.getClients(group, filter.asFilter(), companyGuid)
    }

    override fun getDebts(guid: String, companyGuid: String): Flow<List<Debt>> {
        return dao.getClientDebts(guid, companyGuid)
    }

    override fun getDebt(guid: String, docId: String): Flow<Debt> {
        return dao.getClientDebt(guid, docId).map { debt ->
            debt ?: Debt()
        }
    }

    override fun getLocation(guid: String): Flow<LClientLocation> {
        return dao.getClientLocation(guid).map {
            it ?: LClientLocation()
        }
    }

    override fun getLocations(): Flow<List<LClientLocation>> {
        return dao.getClientLocations().map { locations ->
            locations ?: listOf()
        }
    }

    override suspend fun updateLocation(location: ClientLocation) {
        updateDao.upsertClientLocation(listOf(location))
    }

    override suspend fun deleteLocation(location: ClientLocation) {
        updateDao.deleteClientLocation(location)
    }

}