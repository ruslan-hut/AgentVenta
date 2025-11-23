package ua.com.programmer.agentventa.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ua.com.programmer.agentventa.data.local.entity.ClientLocation
import ua.com.programmer.agentventa.data.local.entity.Debt
import ua.com.programmer.agentventa.data.local.entity.LClient
import ua.com.programmer.agentventa.data.local.entity.LClientLocation
import ua.com.programmer.agentventa.domain.repository.ClientRepository

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
            val matchesGroup = group.isEmpty() || client.groupGuid == group
            val matchesFilter = filter.isEmpty() || client.description.contains(filter, ignoreCase = true) || client.code.contains(filter, ignoreCase = true) || client.address.contains(filter, ignoreCase = true)
            //val matchesCompany = companyGuid.isEmpty() || client.company == companyGuid

            matchesGroup && matchesFilter //&& matchesCompany
        }
    }

    override fun getDebts(guid: String, companyGuid: String): Flow<List<Debt>> = debts.map { list ->
        list.filter { debt ->
            debt.clientGuid == guid && (companyGuid.isEmpty() || debt.companyGuid == companyGuid)
        }
    }

    override fun getDebt(guid: String, docId: String): Flow<Debt> = debts.map { list ->
        list.first { it.clientGuid == guid && it.docGuid == docId }
    }

    override fun getLocation(guid: String): Flow<LClientLocation> = locations.map { list ->
        list.first { it.clientGuid == guid }
    }

    override fun getLocations(): Flow<List<LClientLocation>> = locations

    override suspend fun updateLocation(location: ClientLocation) {
        val currentLocations = locations.value.toMutableList()
        val existingIndex = currentLocations.indexOfFirst { it.clientGuid == location.clientGuid }

        val lLocation = LClientLocation(
            clientGuid = location.clientGuid,
            databaseId = location.databaseId,
            description = "", //location.description ?: "",
            address = location.address,
            latitude = location.latitude,
            longitude = location.longitude
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
        currentLocations.removeIf { it.clientGuid == location.clientGuid }
        locations.value = currentLocations
    }

    // Test helper methods

    fun addClient(client: LClient) {
        clients.value += client
    }

    fun addClients(vararg clientList: LClient) {
        clients.value += clientList
    }

    fun addDebt(debt: Debt) {
        debts.value += debt
    }

    fun addDebts(vararg debtList: Debt) {
        debts.value += debtList
    }

    fun addLocation(location: LClientLocation) {
        locations.value += location
    }

    fun clearAll() {
        clients.value = emptyList()
        debts.value = emptyList()
        locations.value = emptyList()
    }
}
