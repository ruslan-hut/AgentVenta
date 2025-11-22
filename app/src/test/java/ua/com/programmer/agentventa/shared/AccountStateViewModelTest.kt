package ua.com.programmer.agentventa.shared

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ua.com.programmer.agentventa.dao.entity.Company
import ua.com.programmer.agentventa.dao.entity.PaymentType
import ua.com.programmer.agentventa.dao.entity.PriceType
import ua.com.programmer.agentventa.dao.entity.Store
import ua.com.programmer.agentventa.dao.entity.UserAccount
import ua.com.programmer.agentventa.fixtures.TestFixtures
import ua.com.programmer.agentventa.settings.UserOptions
import ua.com.programmer.agentventa.util.MainDispatcherRule

/**
 * Test suite for AccountStateViewModel
 *
 * This ViewModel is a simple delegation wrapper around AccountStateManager,
 * exposing StateFlows to the UI layer for reactive account state.
 *
 * Covers:
 * - StateFlow exposure from AccountStateManager
 * - Current account state access
 * - Options, price types, payment types access
 * - Companies and stores access
 * - Default company/store access
 * - Delegation methods (price type lookups, company/store finds)
 * - Reactive state updates
 */
class AccountStateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var accountStateManager: AccountStateManager
    private lateinit var viewModel: AccountStateViewModel

    // Mock StateFlows
    private lateinit var mockCurrentAccount: MutableStateFlow<UserAccount>
    private lateinit var mockOptions: MutableStateFlow<UserOptions>
    private lateinit var mockPriceTypes: MutableStateFlow<List<PriceType>>
    private lateinit var mockPaymentTypes: MutableStateFlow<List<PaymentType>>
    private lateinit var mockCompanies: MutableStateFlow<List<Company>>
    private lateinit var mockStores: MutableStateFlow<List<Store>>
    private lateinit var mockDefaultCompany: MutableStateFlow<Company>
    private lateinit var mockDefaultStore: MutableStateFlow<Store>

    @Before
    fun setup() {
        // Initialize StateFlows with test data
        mockCurrentAccount = MutableStateFlow(createTestAccount())
        mockOptions = MutableStateFlow(createTestOptions())
        mockPriceTypes = MutableStateFlow(createTestPriceTypes())
        mockPaymentTypes = MutableStateFlow(createTestPaymentTypes())
        mockCompanies = MutableStateFlow(createTestCompanies())
        mockStores = MutableStateFlow(createTestStores())
        mockDefaultCompany = MutableStateFlow(createTestCompanies()[0])
        mockDefaultStore = MutableStateFlow(createTestStores()[0])

        // Mock AccountStateManager
        accountStateManager = mock {
            on { currentAccount } doReturn mockCurrentAccount
            on { options } doReturn mockOptions
            on { priceTypes } doReturn mockPriceTypes
            on { paymentTypes } doReturn mockPaymentTypes
            on { companies } doReturn mockCompanies
            on { stores } doReturn mockStores
            on { defaultCompany } doReturn mockDefaultCompany
            on { defaultStore } doReturn mockDefaultStore
            on { getPriceTypeCode("Retail Price") } doReturn "P1"
            on { getPriceDescription("P1") } doReturn "Retail Price"
            on { getPaymentType("Cash") } doReturn createTestPaymentTypes()[0]
            on { findCompany("C1") } doReturn createTestCompanies()[0]
            on { findStore("S1") } doReturn createTestStores()[0]
        }

        viewModel = AccountStateViewModel(accountStateManager)
    }

    // ========================================
    // Helper Methods
    // ========================================

    private fun createTestAccount() = UserAccount(
        guid = TestFixtures.TEST_ACCOUNT_GUID,
        isCurrent = 1,
        description = "Test Account",
        databaseName = "test_db",
        serverAddress = "http://test.server",
        username = "testuser",
        password = "testpass",
        options = ""
    )

    private fun createTestOptions() = UserOptions(
        loadImages = true,
        useLocation = true,
        showCompanies = true,
        showStores = true
    )

    private fun createTestPriceTypes() = listOf(
        PriceType(guid = "P1", description = "Retail Price", code = "P1", db_guid = "test"),
        PriceType(guid = "P2", description = "Wholesale Price", code = "P2", db_guid = "test"),
        PriceType(guid = "P3", description = "VIP Price", code = "P3", db_guid = "test")
    )

    private fun createTestPaymentTypes() = listOf(
        PaymentType(guid = "PAY1", description = "Cash", code = "CASH", db_guid = "test"),
        PaymentType(guid = "PAY2", description = "Card", code = "CARD", db_guid = "test"),
        PaymentType(guid = "PAY3", description = "Transfer", code = "TRANSFER", db_guid = "test")
    )

    private fun createTestCompanies() = listOf(
        Company(guid = "C1", description = "Company 1", code = "C1", db_guid = "test", isDefault = 1),
        Company(guid = "C2", description = "Company 2", code = "C2", db_guid = "test", isDefault = 0),
        Company(guid = "C3", description = "Company 3", code = "C3", db_guid = "test", isDefault = 0)
    )

    private fun createTestStores() = listOf(
        Store(guid = "S1", description = "Store 1", code = "S1", db_guid = "test", isDefault = 1),
        Store(guid = "S2", description = "Store 2", code = "S2", db_guid = "test", isDefault = 0),
        Store(guid = "S3", description = "Store 3", code = "S3", db_guid = "test", isDefault = 0)
    )

    // ========================================
    // StateFlow Exposure Tests
    // ========================================

    @Test
    fun `currentAccount StateFlow exposes AccountStateManager current account`() = runTest {
        viewModel.currentAccount.test {
            val account = awaitItem()
            assertThat(account.guid).isEqualTo(TestFixtures.TEST_ACCOUNT_GUID)
            assertThat(account.description).isEqualTo("Test Account")
            assertThat(account.isCurrent).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `currentAccount StateFlow updates when AccountStateManager changes`() = runTest {
        viewModel.currentAccount.test {
            // Initial account
            var account = awaitItem()
            assertThat(account.guid).isEqualTo(TestFixtures.TEST_ACCOUNT_GUID)

            // Update account in manager
            val newAccount = createTestAccount().copy(
                guid = "new-account-guid",
                description = "Updated Account"
            )
            mockCurrentAccount.value = newAccount

            // Should receive updated account
            account = awaitItem()
            assertThat(account.guid).isEqualTo("new-account-guid")
            assertThat(account.description).isEqualTo("Updated Account")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `options StateFlow exposes AccountStateManager options`() = runTest {
        viewModel.options.test {
            val options = awaitItem()
            assertThat(options.loadImages).isTrue()
            assertThat(options.useLocation).isTrue()
            assertThat(options.showCompanies).isTrue()
            assertThat(options.showStores).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `options StateFlow updates when AccountStateManager changes`() = runTest {
        viewModel.options.test {
            // Initial options
            var options = awaitItem()
            assertThat(options.loadImages).isTrue()

            // Update options
            mockOptions.value = UserOptions(
                loadImages = false,
                useLocation = false,
                showCompanies = false,
                showStores = false
            )

            // Should receive updated options
            options = awaitItem()
            assertThat(options.loadImages).isFalse()
            assertThat(options.useLocation).isFalse()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `priceTypes StateFlow exposes AccountStateManager price types`() = runTest {
        viewModel.priceTypes.test {
            val priceTypes = awaitItem()
            assertThat(priceTypes).hasSize(3)
            assertThat(priceTypes[0].description).isEqualTo("Retail Price")
            assertThat(priceTypes[1].description).isEqualTo("Wholesale Price")
            assertThat(priceTypes[2].description).isEqualTo("VIP Price")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `priceTypes StateFlow updates when AccountStateManager changes`() = runTest {
        viewModel.priceTypes.test {
            // Initial price types
            var priceTypes = awaitItem()
            assertThat(priceTypes).hasSize(3)

            // Update price types (e.g., different account with different prices)
            val newPriceTypes = listOf(
                PriceType(guid = "P10", description = "Special Price", code = "SP", db_guid = "test")
            )
            mockPriceTypes.value = newPriceTypes

            // Should receive updated list
            priceTypes = awaitItem()
            assertThat(priceTypes).hasSize(1)
            assertThat(priceTypes[0].description).isEqualTo("Special Price")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `paymentTypes StateFlow exposes AccountStateManager payment types`() = runTest {
        viewModel.paymentTypes.test {
            val paymentTypes = awaitItem()
            assertThat(paymentTypes).hasSize(3)
            assertThat(paymentTypes[0].description).isEqualTo("Cash")
            assertThat(paymentTypes[1].description).isEqualTo("Card")
            assertThat(paymentTypes[2].description).isEqualTo("Transfer")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `paymentTypes StateFlow updates when AccountStateManager changes`() = runTest {
        viewModel.paymentTypes.test {
            // Initial payment types
            var paymentTypes = awaitItem()
            assertThat(paymentTypes).hasSize(3)

            // Update payment types
            val newPaymentTypes = listOf(
                PaymentType(guid = "PAY10", description = "Bitcoin", code = "BTC", db_guid = "test")
            )
            mockPaymentTypes.value = newPaymentTypes

            // Should receive updated list
            paymentTypes = awaitItem()
            assertThat(paymentTypes).hasSize(1)
            assertThat(paymentTypes[0].description).isEqualTo("Bitcoin")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `companies StateFlow exposes AccountStateManager companies`() = runTest {
        viewModel.companies.test {
            val companies = awaitItem()
            assertThat(companies).hasSize(3)
            assertThat(companies[0].description).isEqualTo("Company 1")
            assertThat(companies[1].description).isEqualTo("Company 2")
            assertThat(companies[2].description).isEqualTo("Company 3")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `companies StateFlow updates when AccountStateManager changes`() = runTest {
        viewModel.companies.test {
            // Initial companies
            var companies = awaitItem()
            assertThat(companies).hasSize(3)

            // Update companies
            val newCompanies = listOf(
                Company(guid = "C10", description = "New Company", code = "NC", db_guid = "test", isDefault = 1)
            )
            mockCompanies.value = newCompanies

            // Should receive updated list
            companies = awaitItem()
            assertThat(companies).hasSize(1)
            assertThat(companies[0].description).isEqualTo("New Company")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stores StateFlow exposes AccountStateManager stores`() = runTest {
        viewModel.stores.test {
            val stores = awaitItem()
            assertThat(stores).hasSize(3)
            assertThat(stores[0].description).isEqualTo("Store 1")
            assertThat(stores[1].description).isEqualTo("Store 2")
            assertThat(stores[2].description).isEqualTo("Store 3")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stores StateFlow updates when AccountStateManager changes`() = runTest {
        viewModel.stores.test {
            // Initial stores
            var stores = awaitItem()
            assertThat(stores).hasSize(3)

            // Update stores
            val newStores = listOf(
                Store(guid = "S10", description = "New Store", code = "NS", db_guid = "test", isDefault = 1)
            )
            mockStores.value = newStores

            // Should receive updated list
            stores = awaitItem()
            assertThat(stores).hasSize(1)
            assertThat(stores[0].description).isEqualTo("New Store")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `defaultCompany StateFlow exposes AccountStateManager default company`() = runTest {
        viewModel.defaultCompany.test {
            val company = awaitItem()
            assertThat(company.guid).isEqualTo("C1")
            assertThat(company.description).isEqualTo("Company 1")
            assertThat(company.isDefault).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `defaultCompany StateFlow updates when AccountStateManager changes`() = runTest {
        viewModel.defaultCompany.test {
            // Initial default
            var company = awaitItem()
            assertThat(company.guid).isEqualTo("C1")

            // Change default company
            val newDefault = createTestCompanies()[1]
            mockDefaultCompany.value = newDefault

            // Should receive updated default
            company = awaitItem()
            assertThat(company.guid).isEqualTo("C2")
            assertThat(company.description).isEqualTo("Company 2")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `defaultStore StateFlow exposes AccountStateManager default store`() = runTest {
        viewModel.defaultStore.test {
            val store = awaitItem()
            assertThat(store.guid).isEqualTo("S1")
            assertThat(store.description).isEqualTo("Store 1")
            assertThat(store.isDefault).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `defaultStore StateFlow updates when AccountStateManager changes`() = runTest {
        viewModel.defaultStore.test {
            // Initial default
            var store = awaitItem()
            assertThat(store.guid).isEqualTo("S1")

            // Change default store
            val newDefault = createTestStores()[1]
            mockDefaultStore.value = newDefault

            // Should receive updated default
            store = awaitItem()
            assertThat(store.guid).isEqualTo("S2")
            assertThat(store.description).isEqualTo("Store 2")

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // Delegation Method Tests
    // ========================================

    @Test
    fun `getPriceTypeCode delegates to AccountStateManager`() {
        // Act
        val code = viewModel.getPriceTypeCode("Retail Price")

        // Assert
        assertThat(code).isEqualTo("P1")
        verify(accountStateManager).getPriceTypeCode("Retail Price")
    }

    @Test
    fun `getPriceDescription delegates to AccountStateManager`() {
        // Act
        val description = viewModel.getPriceDescription("P1")

        // Assert
        assertThat(description).isEqualTo("Retail Price")
        verify(accountStateManager).getPriceDescription("P1")
    }

    @Test
    fun `getPaymentType delegates to AccountStateManager`() {
        // Act
        val paymentType = viewModel.getPaymentType("Cash")

        // Assert
        assertThat(paymentType.description).isEqualTo("Cash")
        assertThat(paymentType.code).isEqualTo("CASH")
        verify(accountStateManager).getPaymentType("Cash")
    }

    @Test
    fun `findCompany delegates to AccountStateManager and returns company`() {
        // Act
        val company = viewModel.findCompany("C1")

        // Assert
        assertThat(company).isNotNull()
        assertThat(company?.guid).isEqualTo("C1")
        assertThat(company?.description).isEqualTo("Company 1")
        verify(accountStateManager).findCompany("C1")
    }

    @Test
    fun `findCompany with non-existent GUID returns null`() {
        // Arrange
        whenever(accountStateManager.findCompany("invalid")) doReturn null

        // Act
        val company = viewModel.findCompany("invalid")

        // Assert
        assertThat(company).isNull()
        verify(accountStateManager).findCompany("invalid")
    }

    @Test
    fun `findStore delegates to AccountStateManager and returns store`() {
        // Act
        val store = viewModel.findStore("S1")

        // Assert
        assertThat(store).isNotNull()
        assertThat(store?.guid).isEqualTo("S1")
        assertThat(store?.description).isEqualTo("Store 1")
        verify(accountStateManager).findStore("S1")
    }

    @Test
    fun `findStore with non-existent GUID returns null`() {
        // Arrange
        whenever(accountStateManager.findStore("invalid")) doReturn null

        // Act
        val store = viewModel.findStore("invalid")

        // Assert
        assertThat(store).isNull()
        verify(accountStateManager).findStore("invalid")
    }

    // ========================================
    // Reactive State Integration Tests
    // ========================================

    @Test
    fun `all StateFlows are reactive to manager changes`() = runTest {
        // Test that multiple StateFlows can be updated simultaneously
        viewModel.currentAccount.test {
            skipItems(1) // Skip initial

            // Simulate account switch - all related data changes
            mockCurrentAccount.value = createTestAccount().copy(guid = "account-2")
            mockPriceTypes.value = emptyList()
            mockPaymentTypes.value = emptyList()
            mockCompanies.value = emptyList()
            mockStores.value = emptyList()

            // Current account should update
            val account = awaitItem()
            assertThat(account.guid).isEqualTo("account-2")

            cancelAndIgnoreRemainingEvents()
        }

        // Verify other flows also updated
        viewModel.priceTypes.test {
            val priceTypes = awaitItem()
            assertThat(priceTypes).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.companies.test {
            val companies = awaitItem()
            assertThat(companies).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty lists are handled correctly`() = runTest {
        // Update to empty lists
        mockPriceTypes.value = emptyList()
        mockPaymentTypes.value = emptyList()
        mockCompanies.value = emptyList()
        mockStores.value = emptyList()

        // All should be empty
        viewModel.priceTypes.test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.paymentTypes.test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.companies.test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.stores.test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `default company and store remain accessible when lists are empty`() = runTest {
        // Empty lists but keep defaults
        mockCompanies.value = emptyList()
        mockStores.value = emptyList()

        // Defaults should still be available
        viewModel.defaultCompany.test {
            val company = awaitItem()
            assertThat(company.guid).isEqualTo("C1")
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.defaultStore.test {
            val store = awaitItem()
            assertThat(store.guid).isEqualTo("S1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `multiple rapid state changes emit all updates`() = runTest {
        viewModel.currentAccount.test {
            skipItems(1) // Skip initial

            // Rapid account changes
            mockCurrentAccount.value = createTestAccount().copy(guid = "account-1")
            mockCurrentAccount.value = createTestAccount().copy(guid = "account-2")
            mockCurrentAccount.value = createTestAccount().copy(guid = "account-3")

            // Should receive all updates
            assertThat(awaitItem().guid).isEqualTo("account-1")
            assertThat(awaitItem().guid).isEqualTo("account-2")
            assertThat(awaitItem().guid).isEqualTo("account-3")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delegation methods work with empty or null responses`() {
        // Arrange
        whenever(accountStateManager.getPriceTypeCode("invalid")) doReturn ""
        whenever(accountStateManager.getPriceDescription("invalid")) doReturn ""
        whenever(accountStateManager.findCompany("invalid")) doReturn null
        whenever(accountStateManager.findStore("invalid")) doReturn null

        // Act & Assert
        assertThat(viewModel.getPriceTypeCode("invalid")).isEmpty()
        assertThat(viewModel.getPriceDescription("invalid")).isEmpty()
        assertThat(viewModel.findCompany("invalid")).isNull()
        assertThat(viewModel.findStore("invalid")).isNull()
    }

    @Test
    fun `large lists of price types are handled correctly`() = runTest {
        // Arrange: 100 price types
        val largePriceTypeList = (1..100).map { index ->
            PriceType(
                guid = "P$index",
                description = "Price Type $index",
                code = "PT$index",
                db_guid = "test"
            )
        }
        mockPriceTypes.value = largePriceTypeList

        // Act & Assert
        viewModel.priceTypes.test {
            val priceTypes = awaitItem()
            assertThat(priceTypes).hasSize(100)
            assertThat(priceTypes.first().description).isEqualTo("Price Type 1")
            assertThat(priceTypes.last().description).isEqualTo("Price Type 100")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `account with all fields populated exposes correctly`() = runTest {
        // Arrange: Account with all fields
        val fullAccount = UserAccount(
            guid = "full-account",
            isCurrent = 1,
            description = "Full Account",
            databaseName = "production_db",
            serverAddress = "https://production.server.com",
            username = "admin",
            password = "securepass",
            options = """{"loadImages": true, "useLocation": true}"""
        )
        mockCurrentAccount.value = fullAccount

        // Act & Assert
        viewModel.currentAccount.test {
            val account = awaitItem()
            assertThat(account.guid).isEqualTo("full-account")
            assertThat(account.description).isEqualTo("Full Account")
            assertThat(account.databaseName).isEqualTo("production_db")
            assertThat(account.serverAddress).isEqualTo("https://production.server.com")
            assertThat(account.username).isEqualTo("admin")
            assertThat(account.options).contains("loadImages")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
