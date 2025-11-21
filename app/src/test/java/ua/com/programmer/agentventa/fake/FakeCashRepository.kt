package ua.com.programmer.agentventa.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ua.com.programmer.agentventa.dao.entity.Cash
import ua.com.programmer.agentventa.dao.entity.Client
import ua.com.programmer.agentventa.dao.entity.Company
import ua.com.programmer.agentventa.documents.DocumentTotals
import ua.com.programmer.agentventa.repository.CashRepository
import java.util.*

/**
 * Fake implementation of CashRepository for testing.
 * Provides in-memory storage for cash documents.
 */
class FakeCashRepository(
    private val currentAccountGuid: String = FakeUserAccountRepository.TEST_ACCOUNT_GUID
) : CashRepository {

    private val cashDocuments = MutableStateFlow<List<Cash>>(emptyList())
    private val clients = MutableStateFlow<List<Client>>(emptyList())
    private val companies = MutableStateFlow<List<Company>>(emptyList())

    override fun getDocument(guid: String): Flow<Cash> = cashDocuments.map { list ->
        list.first { it.guid == guid }
    }

    override suspend fun newDocument(): Cash {
        return Cash(
            guid = UUID.randomUUID().toString(),
            db_guid = currentAccountGuid,
            date = Date(),
            time = Date(),
            isSent = 0,
            isProcessed = 0,
            sum = 0.0
        )
    }

    override fun getDocuments(filter: String, listDate: Date?): Flow<List<Cash>> = cashDocuments.map { list ->
        list.filter { cash ->
            val matchesFilter = filter.isEmpty() ||
                cash.client?.contains(filter, ignoreCase = true) == true ||
                cash.number?.contains(filter, ignoreCase = true) == true

            val matchesDate = listDate == null || isSameDay(cash.date, listDate)

            matchesFilter && matchesDate
        }
    }

    override suspend fun updateDocument(document: Cash): Boolean {
        val currentList = cashDocuments.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.guid == document.guid }

        if (existingIndex >= 0) {
            currentList[existingIndex] = document
        } else {
            currentList.add(document)
        }

        cashDocuments.value = currentList
        return true
    }

    override suspend fun deleteDocument(document: Cash): Boolean {
        val currentList = cashDocuments.value.toMutableList()
        val removed = currentList.removeIf { it.guid == document.guid }
        cashDocuments.value = currentList
        return removed
    }

    override fun getDocumentListTotals(filter: String, listDate: Date?): Flow<List<DocumentTotals>> {
        return getDocuments(filter, listDate).map { cashList ->
            if (cashList.isEmpty()) {
                emptyList()
            } else {
                listOf(calculateTotals(cashList))
            }
        }
    }

    override suspend fun setCompany(guid: String, company: Company) {
        val cash = cashDocuments.value.firstOrNull { it.guid == guid } ?: return
        updateDocument(cash.copy(
            companyGuid = company.guid,
            company = company.description
        ))
    }

    override suspend fun setClient(guid: String, client: Client) {
        val cash = cashDocuments.value.firstOrNull { it.guid == guid } ?: return
        updateDocument(cash.copy(
            clientGuid = client.guid,
            client = client.description
        ))
    }

    // Test helper methods

    fun addCash(cash: Cash) {
        cashDocuments.value = cashDocuments.value + cash
    }

    fun addClient(client: Client) {
        clients.value = clients.value + client
    }

    fun addCompany(company: Company) {
        companies.value = companies.value + company
    }

    fun clearAll() {
        cashDocuments.value = emptyList()
        clients.value = emptyList()
        companies.value = emptyList()
    }

    private fun calculateTotals(cashList: List<Cash>): DocumentTotals {
        return DocumentTotals(
            documents = cashList.count { it.isReturn == 0 },
            returns = cashList.count { it.isReturn == 1 },
            sum = cashList.filter { it.isReturn == 0 }.sumOf { it.sum ?: 0.0 },
            sumReturn = cashList.filter { it.isReturn == 1 }.sumOf { it.sum ?: 0.0 }
        )
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}
