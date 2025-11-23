package ua.com.programmer.agentventa.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import ua.com.programmer.agentventa.data.local.dao.ClientDao
import ua.com.programmer.agentventa.data.local.dao.DataExchangeDao
import ua.com.programmer.agentventa.data.local.entity.ClientLocation
import ua.com.programmer.agentventa.data.local.entity.Debt
import ua.com.programmer.agentventa.data.local.entity.LClient
import ua.com.programmer.agentventa.data.local.entity.LClientLocation
import ua.com.programmer.agentventa.extensions.asFilter
import ua.com.programmer.agentventa.domain.repository.ClientRepository
import ua.com.programmer.agentventa.domain.repository.UserAccountRepository
import javax.inject.Inject

class ClientRepositoryImpl @Inject constructor(
    private val dao: ClientDao,
    private val updateDao: DataExchangeDao,
    private val userAccountRepository: UserAccountRepository
): ClientRepository {

    private suspend fun getCurrentDbGuid(): String = userAccountRepository.currentAccountGuid.first()

    override fun getClient(guid: String, companyGuid: String): Flow<LClient> {
        return userAccountRepository.currentAccountGuid.flatMapLatest { currentDbGuid ->
            dao.getClientInfo(currentDbGuid, guid, companyGuid).map {
                it ?: LClient()
            }
        }
    }

    override fun getClients(group: String, filter: String, companyGuid: String): Flow<List<LClient>> {
        return userAccountRepository.currentAccountGuid.flatMapLatest { currentDbGuid ->
            dao.getClients(currentDbGuid, group, filter.asFilter(), companyGuid)
        }
    }

    override fun getDebts(guid: String, companyGuid: String): Flow<List<Debt>> {
        return userAccountRepository.currentAccountGuid.flatMapLatest { currentDbGuid ->
            dao.getClientDebts(currentDbGuid, guid, companyGuid)
        }
    }

    override fun getDebt(guid: String, docId: String): Flow<Debt> {
        return userAccountRepository.currentAccountGuid.flatMapLatest { currentDbGuid ->
            dao.getClientDebt(currentDbGuid, guid, docId).map { debt ->
                debt ?: Debt()
            }
        }
    }

    override fun getLocation(guid: String): Flow<LClientLocation> {
        return userAccountRepository.currentAccountGuid.flatMapLatest { currentDbGuid ->
            dao.getClientLocation(currentDbGuid, guid).map {
                it ?: LClientLocation()
            }
        }
    }

    override fun getLocations(): Flow<List<LClientLocation>> {
        return userAccountRepository.currentAccountGuid.flatMapLatest { currentDbGuid ->
            dao.getClientLocations(currentDbGuid).map { locations ->
                locations ?: listOf()
            }
        }
    }

    override suspend fun updateLocation(location: ClientLocation) {
        updateDao.upsertClientLocation(listOf(location))
    }

    override suspend fun deleteLocation(location: ClientLocation) {
        updateDao.deleteClientLocation(location)
    }

}