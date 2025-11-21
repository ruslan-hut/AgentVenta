package ua.com.programmer.agentventa.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ua.com.programmer.agentventa.dao.entity.ClientLocation
import ua.com.programmer.agentventa.dao.entity.Debt
import ua.com.programmer.agentventa.dao.entity.LClient
import ua.com.programmer.agentventa.dao.entity.LClientLocation
import ua.com.programmer.agentventa.repository.ClientRepository

/**
 * Fake implementation of ClientRepository for testing.
 * Provides in-memory storage for client data.
 */
class FakeClientRepository(
    private val currentAccountGuid: String = FakeUserAccountRepository.TEST_ACCOUNT_GUID
) : ClientRepository {

    private val clients = MutableStateFlow<List<LClient>>(emptyList())
    private val debts = MutableStateFlow<List<Debt>>(emptyList())
    private val locations = MutableStateFlow<List<LClientLocation>>(emptyList())

    override fun getClient(guid: String, companyGuid: String): Flow<LClient> = clients.map { list ->
        list.first { it.guid == guid }
    }

    override fun getClients(group: String, filter: String, companyGuid: String): Flow<List<LClient>> = clients.map { list ->
        list.filter { client ->
            val matchesGroup = group.isEmpty() || client.group == group
            val matchesFilter = filter.isEmpty() ||
                client.description?.contains(filter, ignoreCase = true) == true ||
                client.code?.contains(filter, ignoreCase = true) == true ||
                client.address?.contains(filter, ignoreCase = true) == true
            val matchesCompany = companyGuid.isEmpty() || client.company == companyGuid

            matchesGroup && matchesFilter && matchesCompany
        }
    }

    override fun getDebts(guid: String, companyGuid: String): Flow<List<Debt>> = debts.map { list ->
        list.filter { debt ->
            debt.client == guid && (companyGuid.isEmpty() || debt.company == companyGuid)
        }
    }

    override fun getDebt(guid: String, docId: String): Flow<Debt> = debts.map { list ->
        list.first { it.client == guid && it.doc == docId }
    }

    override fun getLocation(guid: String): Flow<LClientLocation> = locations.map { list ->
        list.first { it.guid == guid }
    }

    override fun getLocations(): Flow<List<LClientLocation>> = locations

    override suspend fun updateLocation(location: ClientLocation) {
        val currentLocations = locations.value.toMutableList()
        val existingIndex = currentLocations.indexOfFirst { it.guid == location.guid }

        val lLocation = LClientLocation(
            guid = location.guid,
            db_guid = location.db_guid,
            description = location.description ?: "",
            address = location.address ?: "",
            latitude = location.latitude ?: 0.0,
            longitude = location.longitude ?: 0.0
        )

        if (existingIndex >= 0) {
            currentLocations[existingIndex] = lLocation
        } else {
            currentLocations.add(lLocation)
        }

        locations.value = currentLocations
    }

    override suspend fun deleteLocation(location: ClientLocation) {
        val currentLocations = locations.value.toMutableList()
        currentLocations.removeIf { it.guid == location.guid }
        locations.value = currentLocations
    }

    // Test helper methods

    fun addClient(client: LClient) {
        clients.value = clients.value + client
    }

    fun addClients(vararg clientList: LClient) {
        clients.value = clients.value + clientList
    }

    fun addDebt(debt: Debt) {
        debts.value = debts.value + debt
    }

    fun addDebts(vararg debtList: Debt) {
        debts.value = debts.value + debtList
    }

    fun addLocation(location: LClientLocation) {
        locations.value = locations.value + location
    }

    fun clearAll() {
        clients.value = emptyList()
        debts.value = emptyList()
        locations.value = emptyList()
    }
}
