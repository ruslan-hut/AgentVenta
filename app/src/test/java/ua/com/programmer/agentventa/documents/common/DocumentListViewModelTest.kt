package ua.com.programmer.agentventa.documents.common

import android.view.View
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import ua.com.programmer.agentventa.dao.entity.Order
import ua.com.programmer.agentventa.fake.FakeOrderRepository
import ua.com.programmer.agentventa.fake.FakeUserAccountRepository
import ua.com.programmer.agentventa.fixtures.TestFixtures
import ua.com.programmer.agentventa.util.MainDispatcherRule
import ua.com.programmer.agentventa.util.getOrAwaitValue
import java.util.*

/**
 * Test suite for DocumentListViewModel base class.
 * Tests document list loading, filtering, search, date filtering, and totals calculation.
 *
 * Uses Order as the concrete document type for testing the generic DocumentListViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var userAccountRepository: FakeUserAccountRepository
    private lateinit var counterFormatter: CounterFormatter
    private lateinit var viewModel: DocumentListViewModel<Order>

    @Before
    fun setup() {
        orderRepository = FakeOrderRepository(TestFixtures.TEST_DB_GUID)
        userAccountRepository = FakeUserAccountRepository()
        counterFormatter = DefaultCounterFormatter()

        // Setup test account
        userAccountRepository.setupTestAccount()

        viewModel = DocumentListViewModel(
            repository = orderRepository,
            userAccountRepository = userAccountRepository,
            counterFormatter = counterFormatter
        )
    }

    @After
    fun tearDown() {
        orderRepository.clearAll()
        userAccountRepository.clearAccounts()
    }

    // ========== Documents List Loading Tests ==========

    @Test
    fun `documents list loads from repository`() = runTest {
        // Arrange
        val order1 = TestFixtures.createOrder1()
        val order2 = TestFixtures.createOrder2Return()
        val order3 = TestFixtures.createOrder3Sent()
        orderRepository.addOrder(order1)
        orderRepository.addOrder(order2)
        orderRepository.addOrder(order3)

        // Act & Assert
        viewModel.documentsFlow.test {
            val documents = awaitItem()
            assertThat(documents).hasSize(3)
            assertThat(documents.map { it.guid }).containsExactly(
                order1.guid,
                order2.guid,
                order3.guid
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial state has empty documents list`() = runTest {
        // Assert
        assertThat(viewModel.documentsFlow.value).isEmpty()
    }

    @Test
    fun `documents list updates when repository changes`() = runTest {
        // Arrange
        viewModel.documentsFlow.test {
            // Initial empty list
            assertThat(awaitItem()).isEmpty()

            // Add order
            val order = TestFixtures.createOrder1()
            orderRepository.addOrder(order)

            // Should emit updated list
            val documents = awaitItem()
            assertThat(documents).hasSize(1)
            assertThat(documents[0].guid).isEqualTo(order.guid)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `documents LiveData exposes documentsFlow for backward compatibility`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        orderRepository.addOrder(order)

        // Act
        advanceUntilIdle()
        val documents = viewModel.documents.getOrAwaitValue()

        // Assert
        assertThat(documents).hasSize(1)
        assertThat(documents[0].guid).isEqualTo(order.guid)
    }

    // ========== Filter and Search Tests ==========

    @Test
    fun `filter updates trigger new queries`() = runTest {
        // Arrange
        val order1 = TestFixtures.createOrder1() // Client: ABC Retail Store
        val order2 = TestFixtures.createOrder2Return() // Client: XYZ Wholesale Co
        orderRepository.addOrder(order1)
        orderRepository.addOrder(order2)

        // Act & Assert
        viewModel.documentsFlow.test {
            // Initial - all documents
            assertThat(awaitItem()).hasSize(2)

            // Filter by "ABC"
            viewModel.setSearchText("ABC")
            val filteredDocs = awaitItem()
            assertThat(filteredDocs).hasSize(1)
            assertThat(filteredDocs[0].clientDescription).contains("ABC")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search text updates trigger document reload`() = runTest {
        // Arrange
        val order1 = TestFixtures.createOrder1()
        orderRepository.addOrder(order1)

        // Act & Assert
        viewModel.searchText.test {
            assertThat(awaitItem()).isEmpty() // Initial

            viewModel.setSearchText("test")
            assertThat(awaitItem()).isEqualTo("test")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onTextChanged updates search text StateFlow`() = runTest {
        // Arrange
        val searchQuery = "ABC Retail"

        // Act
        viewModel.onTextChanged(searchQuery, 0, 0, searchQuery.length)
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.searchText.value).isEqualTo(searchQuery)
    }

    @Test
    fun `toggleSearchVisibility shows search field`() = runTest {
        // Arrange
        assertThat(viewModel.searchVisible.value).isFalse()

        // Act
        viewModel.toggleSearchVisibility()
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.searchVisible.value).isTrue()
        assertThat(viewModel.uiState.value.searchVisible).isTrue()
    }

    @Test
    fun `toggleSearchVisibility hides search and clears text`() = runTest {
        // Arrange
        viewModel.setSearchText("test query")
        viewModel.toggleSearchVisibility() // Show
        assertThat(viewModel.searchVisible.value).isTrue()

        // Act
        viewModel.toggleSearchVisibility() // Hide

        // Assert
        assertThat(viewModel.searchVisible.value).isFalse()
        assertThat(viewModel.searchText.value).isEmpty()
        assertThat(viewModel.uiState.value.searchVisible).isFalse()
    }

    @Test
    fun `searchVisibility LiveData returns VISIBLE when search is shown`() = runTest {
        // Act
        viewModel.toggleSearchVisibility()
        advanceUntilIdle()

        // Assert
        val visibility = viewModel.searchVisibility.getOrAwaitValue()
        assertThat(visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun `searchVisibility LiveData returns GONE when search is hidden`() = runTest {
        // Arrange
        viewModel.toggleSearchVisibility() // Show
        viewModel.toggleSearchVisibility() // Hide
        advanceUntilIdle()

        // Assert
        val visibility = viewModel.searchVisibility.getOrAwaitValue()
        assertThat(visibility).isEqualTo(View.GONE)
    }

    // ========== Date Filtering Tests ==========

    @Test
    fun `date filtering works correctly`() = runTest {
        // Arrange
        val today = Date()
        val yesterday = Date(today.time - 86400000) // 1 day ago

        val orderToday = TestFixtures.createOrder1().copy(
            date = formatDate(today)
        )
        val orderYesterday = TestFixtures.createOrder2Return().copy(
            date = formatDate(yesterday)
        )
        orderRepository.addOrder(orderToday)
        orderRepository.addOrder(orderYesterday)

        // Act & Assert
        viewModel.documentsFlow.test {
            // Initial - today's date filter
            val todayDocs = awaitItem()
            assertThat(todayDocs.any { it.guid == orderToday.guid }).isTrue()

            // Change to yesterday
            viewModel.setDate(yesterday)
            val yesterdayDocs = awaitItem()
            assertThat(yesterdayDocs.any { it.guid == orderYesterday.guid }).isTrue()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setDate updates listDateFlow`() = runTest {
        // Arrange
        val testDate = Date(System.currentTimeMillis() - 86400000)

        // Act
        viewModel.setDate(testDate)
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.listDateFlow.value).isEqualTo(testDate)
    }

    @Test
    fun `setDate with null shows all documents`() = runTest {
        // Arrange
        val order1 = TestFixtures.createOrder1()
        val order2 = TestFixtures.createOrder2Return()
        orderRepository.addOrder(order1)
        orderRepository.addOrder(order2)

        // Act
        viewModel.documentsFlow.test {
            awaitItem() // Initial

            viewModel.setDate(null)

            // Assert - should show all documents regardless of date
            val allDocs = awaitItem()
            assertThat(allDocs).hasSize(2)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `listDate LiveData exposes date for data binding`() = runTest {
        // Arrange
        val testDate = Date()

        // Act
        viewModel.setDate(testDate)
        advanceUntilIdle()

        // Assert
        val date = viewModel.listDate.getOrAwaitValue()
        assertThat(date).isEqualTo(testDate)
    }

    // ========== Document Totals Tests ==========

    @Test
    fun `document totals calculation is correct`() = runTest {
        // Arrange
        val order1 = TestFixtures.createOrder1()
        val order2 = TestFixtures.createOrder2Return()
        orderRepository.addOrder(order1)
        orderRepository.addOrder(order2)

        // Act & Assert
        viewModel.totalsFlow.test {
            val totals = awaitItem()
            assertThat(totals).hasSize(1)

            val total = totals[0]
            assertThat(total.documents).isEqualTo(1) // order1 is not a return
            assertThat(total.returns).isEqualTo(1)   // order2 is a return

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `totals flow updates when filter changes`() = runTest {
        // Arrange
        val order1 = TestFixtures.createOrder1()
        val order2 = TestFixtures.createOrder2Return()
        orderRepository.addOrder(order1)
        orderRepository.addOrder(order2)

        // Act & Assert
        viewModel.totalsFlow.test {
            val initialTotals = awaitItem()
            assertThat(initialTotals[0].documents + initialTotals[0].returns).isEqualTo(2)

            // Filter to show only order1
            viewModel.setSearchText("ABC") // order1 client name

            val filteredTotals = awaitItem()
            assertThat(filteredTotals[0].documents).isEqualTo(1)
            assertThat(filteredTotals[0].returns).isEqualTo(0)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `totals LiveData exposes totalsFlow for backward compatibility`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        orderRepository.addOrder(order)

        // Act
        advanceUntilIdle()
        val totals = viewModel.totals.getOrAwaitValue()

        // Assert
        assertThat(totals).hasSize(1)
        assertThat(totals[0].documents).isEqualTo(1)
    }

    @Test
    fun `updateCounters formats totals correctly`() = runTest {
        // Arrange
        val totals = listOf(TestFixtures.createDocumentTotalsForOrder1())

        // Act
        viewModel.updateCounters(totals)
        advanceUntilIdle()

        // Assert
        val uiState = viewModel.uiState.value
        assertThat(uiState.documentsCount).isEqualTo("1")
        assertThat(uiState.returnsCount).isEqualTo("0")
        assertThat(uiState.totalWeight).isEqualTo("7.500") // 3 decimal places
        assertThat(uiState.totalSum).isEqualTo("1500.00")  // 2 decimal places
        assertThat(uiState.noDataVisible).isFalse() // Has data
    }

    @Test
    fun `updateCounters with empty list shows no data`() = runTest {
        // Act
        viewModel.updateCounters(emptyList())
        advanceUntilIdle()

        // Assert
        val uiState = viewModel.uiState.value
        assertThat(uiState.noDataVisible).isTrue()
        assertThat(uiState.documentsCount).isEqualTo("0")
        assertThat(uiState.returnsCount).isEqualTo("0")
    }

    @Test
    fun `counters auto-update when totals change`() = runTest {
        // Arrange - ViewModel already listening to totalsFlow in init
        viewModel.uiState.test {
            val initialState = awaitItem()
            assertThat(initialState.noDataVisible).isTrue()

            // Add order
            val order = TestFixtures.createOrder1()
            orderRepository.addOrder(order)

            // Should auto-update counters
            val updatedState = awaitItem()
            assertThat(updatedState.noDataVisible).isFalse()
            assertThat(updatedState.documentsCount).isNotEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========== UI State Tests ==========

    @Test
    fun `initial UI state is correct`() {
        // Assert
        val state = viewModel.uiState.value
        assertThat(state.documentsCount).isEqualTo("-")
        assertThat(state.returnsCount).isEqualTo("-")
        assertThat(state.totalWeight).isEqualTo("0.0")
        assertThat(state.totalSum).isEqualTo("0.00")
        assertThat(state.noDataVisible).isTrue()
        assertThat(state.totalsVisible).isFalse()
        assertThat(state.searchVisible).isFalse()
        assertThat(state.searchText).isEmpty()
        assertThat(state.listDate).isNotNull()
    }

    @Test
    fun `setTotalsVisible updates UI state`() = runTest {
        // Act
        viewModel.setTotalsVisible(true)
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.uiState.value.totalsVisible).isTrue()
    }

    @Test
    fun `totalsVisibility LiveData returns VISIBLE when totals shown`() = runTest {
        // Act
        viewModel.setTotalsVisible(true)
        advanceUntilIdle()

        // Assert
        val visibility = viewModel.totalsVisibility.getOrAwaitValue()
        assertThat(visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun `totalsVisibility LiveData returns GONE when totals hidden`() = runTest {
        // Act
        viewModel.setTotalsVisible(false)
        advanceUntilIdle()

        // Assert
        val visibility = viewModel.totalsVisibility.getOrAwaitValue()
        assertThat(visibility).isEqualTo(View.GONE)
    }

    @Test
    fun `noDataTextVisibility returns VISIBLE when no data`() = runTest {
        // Arrange - no orders in repository
        advanceUntilIdle()

        // Assert
        val visibility = viewModel.noDataTextVisibility.getOrAwaitValue()
        assertThat(visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun `noDataTextVisibility returns GONE when has data`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        orderRepository.addOrder(order)
        advanceUntilIdle()

        // Assert
        val visibility = viewModel.noDataTextVisibility.getOrAwaitValue()
        assertThat(visibility).isEqualTo(View.GONE)
    }

    @Test
    fun `documentsCount LiveData displays formatted count`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        orderRepository.addOrder(order)
        advanceUntilIdle()

        // Assert
        val count = viewModel.documentsCount.getOrAwaitValue()
        assertThat(count).isEqualTo("1")
    }

    @Test
    fun `returnsCount LiveData displays formatted count`() = runTest {
        // Arrange
        val returnOrder = TestFixtures.createOrder2Return()
        orderRepository.addOrder(returnOrder)
        advanceUntilIdle()

        // Assert
        val count = viewModel.returnsCount.getOrAwaitValue()
        assertThat(count).isEqualTo("1")
    }

    @Test
    fun `totalWeight LiveData displays formatted weight`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        orderRepository.addOrder(order)
        advanceUntilIdle()

        // Assert
        val weight = viewModel.totalWeight.getOrAwaitValue()
        assertThat(weight).matches("\\d+\\.\\d{3}") // Format: X.XXX
    }

    @Test
    fun `totalSum LiveData displays formatted sum`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        orderRepository.addOrder(order)
        advanceUntilIdle()

        // Assert
        val sum = viewModel.totalSum.getOrAwaitValue()
        assertThat(sum).matches("\\d+\\.\\d{2}") // Format: X.XX
    }

    // ========== Current Account Integration Tests ==========

    @Test
    fun `current account updates from user account repository`() = runTest {
        // Act & Assert
        viewModel.currentAccount.test {
            val account = awaitItem()
            assertThat(account).isNotNull()
            assertThat(account?.guid).isEqualTo(TestFixtures.TEST_ACCOUNT_GUID)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `documents reload when account changes`() = runTest {
        // Arrange
        val order1 = TestFixtures.createOrder1()
        orderRepository.addOrder(order1)

        viewModel.documentsFlow.test {
            awaitItem() // Initial load

            // Change account
            val newAccount = TestFixtures.createDemoAccount()
            userAccountRepository.saveAccount(newAccount)
            userAccountRepository.setIsCurrent(newAccount.guid)

            // Should trigger reload
            awaitItem() // New account triggers filter change

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========== FlatMapLatest Behavior Tests ==========

    @Test
    fun `flatMapLatest cancels previous query when filter changes`() = runTest {
        // Arrange
        val order1 = TestFixtures.createOrder1()
        val order2 = TestFixtures.createOrder2Return()
        orderRepository.addOrder(order1)
        orderRepository.addOrder(order2)

        // Act & Assert - flatMapLatest should cancel previous emissions
        viewModel.documentsFlow.test {
            assertThat(awaitItem()).hasSize(2)

            // Rapid filter changes - flatMapLatest should only emit final result
            viewModel.setSearchText("A")
            viewModel.setSearchText("AB")
            viewModel.setSearchText("ABC")

            val finalResult = awaitItem()
            // Only ABC filter should be applied
            assertThat(finalResult.all { it.clientDescription?.contains("ABC") == true })
                .isTrue()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `combine emits when any filter parameter changes`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        orderRepository.addOrder(order)

        viewModel.documentsFlow.test {
            awaitItem() // Initial

            // Change search text
            viewModel.setSearchText("test")
            awaitItem() // Emits due to search change

            // Change date
            viewModel.setDate(Date(System.currentTimeMillis() - 86400000))
            awaitItem() // Emits due to date change

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========== Edge Cases and Empty State Tests ==========

    @Test
    fun `empty repository returns empty list`() = runTest {
        // Act & Assert
        assertThat(viewModel.documentsFlow.value).isEmpty()
        assertThat(viewModel.totalsFlow.value).isEmpty()
    }

    @Test
    fun `filter with no matches returns empty list`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        orderRepository.addOrder(order)

        // Act
        viewModel.setSearchText("NonExistentClient")
        advanceUntilIdle()

        // Assert
        viewModel.documentsFlow.test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing search filter shows all documents`() = runTest {
        // Arrange
        val order1 = TestFixtures.createOrder1()
        val order2 = TestFixtures.createOrder2Return()
        orderRepository.addOrder(order1)
        orderRepository.addOrder(order2)

        viewModel.setSearchText("ABC") // Filter to one document
        advanceUntilIdle()

        // Act
        viewModel.documentsFlow.test {
            val filteredDocs = awaitItem()
            assertThat(filteredDocs).hasSize(1)

            viewModel.setSearchText("") // Clear filter

            val allDocs = awaitItem()
            assertThat(allDocs).hasSize(2)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========== Helper Methods ==========

    private fun formatDate(date: Date): String {
        val calendar = Calendar.getInstance()
        calendar.time = date
        return String.format(
            "%04d-%02d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }
}
