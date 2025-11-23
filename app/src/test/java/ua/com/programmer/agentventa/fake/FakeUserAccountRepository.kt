package ua.com.programmer.agentventa.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ua.com.programmer.agentventa.data.local.entity.PriceType
import ua.com.programmer.agentventa.data.local.entity.UserAccount
import ua.com.programmer.agentventa.domain.repository.UserAccountRepository

/**
 * Fake implementation of UserAccountRepository for testing.
 * Provides in-memory storage with known test data and deterministic behavior.
 */
class FakeUserAccountRepository : UserAccountRepository {

    companion object {
        const val TEST_ACCOUNT_GUID = "test-account-123"
        const val TEST_DB_GUID = "test-db-guid"
    }

    private val accounts = MutableStateFlow<List<UserAccount>>(emptyList())
    private val _priceTypes = MutableStateFlow<List<PriceType>?>(null)

    override val currentAccount: Flow<UserAccount?> = accounts.map { list ->
        list.firstOrNull { it.isCurrent == 1 }
    }

    override val currentAccountGuid: Flow<String> = currentAccount.map { account ->
        account?.guid ?: ""
    }

    override val priceTypes: Flow<List<PriceType>?> = _priceTypes

    override suspend fun saveAccount(account: UserAccount): Long {
        val currentList = accounts.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.guid == account.guid }

        if (existingIndex >= 0) {
            currentList[existingIndex] = account
        } else {
            currentList.add(account)
        }

        accounts.value = currentList
        return 1L
    }

    override fun getAll(): Flow<List<UserAccount>> = accounts

    override fun getByGuid(guid: String): Flow<UserAccount> = accounts.map { list ->
        list.first { it.guid == guid }
    }

    override suspend fun setIsCurrent(guid: String) {
        val updatedList = accounts.value.map { account ->
            account.copy(isCurrent = if (account.guid == guid) 1 else 0)
        }
        accounts.value = updatedList
    }

    override suspend fun deleteByGuid(guid: String): Int {
        val currentList = accounts.value.toMutableList()
        val removed = currentList.removeIf { it.guid == guid }
        accounts.value = currentList
        return if (removed) 1 else 0
    }

    override suspend fun getCurrent(): UserAccount? {
        return accounts.value.firstOrNull { it.isCurrent == 1 }
    }

    override suspend fun hasAccounts(): Boolean {
        return accounts.value.isNotEmpty()
    }

    // Test helper methods

    /**
     * Sets up a default test account with known GUID
     */
    fun setupTestAccount() {
        val testAccount = UserAccount(
            guid = TEST_ACCOUNT_GUID,
            isCurrent = 1,
            description = "Test Account",
            license = "test-license",
            dataFormat = "json",
            dbServer = "test-server",
            dbName = "test-db",
            dbUser = "test-user",
            dbPassword = "test-password",
            token = "test-token",
            options = "{}"
        )
        accounts.value = listOf(testAccount)
    }

    /**
     * Sets price types for testing
     */
    fun setPriceTypes(types: List<PriceType>) {
        _priceTypes.value = types
    }

    /**
     * Clears all accounts (for test cleanup)
     */
    fun clearAccounts() {
        accounts.value = emptyList()
        _priceTypes.value = null
    }

    /**
     * Adds multiple accounts for multi-account testing
     */
    fun addAccounts(vararg userAccounts: UserAccount) {
        accounts.value = accounts.value + userAccounts
    }
}
