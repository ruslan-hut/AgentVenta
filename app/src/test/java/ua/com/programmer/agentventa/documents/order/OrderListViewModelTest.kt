package ua.com.programmer.agentventa.documents.order

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ua.com.programmer.agentventa.dao.entity.Order
import ua.com.programmer.agentventa.domain.result.DomainException
import ua.com.programmer.agentventa.domain.result.Result
import ua.com.programmer.agentventa.domain.usecase.order.CopyOrderUseCase
import ua.com.programmer.agentventa.fake.FakeOrderRepository
import ua.com.programmer.agentventa.fake.FakeUserAccountRepository
import ua.com.programmer.agentventa.fixtures.TestFixtures
import ua.com.programmer.agentventa.util.MainDispatcherRule
import java.util.Calendar

/**
 * Test suite for OrderListViewModel
 *
 * Covers:
 * - Order list loading and filtering
 * - Date range filtering
 * - Search text filtering
 * - Document totals calculation
 * - Copy order functionality
 * - Totals visibility
 * - Current account integration
 * - Edge cases and error handling
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OrderListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var userAccountRepository: FakeUserAccountRepository
    private lateinit var copyOrderUseCase: CopyOrderUseCase
    private lateinit var viewModel: OrderListViewModel

    @Before
    fun setup() {
        orderRepository = FakeOrderRepository(FakeUserAccountRepository.TEST_ACCOUNT_GUID)
        userAccountRepository = FakeUserAccountRepository()
        userAccountRepository.setupTestAccount()

        copyOrderUseCase = mock()

        viewModel = OrderListViewModel(
            orderRepository = orderRepository,
            userAccountRepository = userAccountRepository,
            copyOrderUseCase = copyOrderUseCase
        )
    }

    // ========================================
    // Initial State Tests
    // ========================================

    @Test
    fun `initial state has empty order list`() = runTest {
        viewModel.documentsFlow.test {
            val orders = awaitItem()
            assertThat(orders).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial totals visibility is true`() = runTest {
        viewModel.uiState.test {
            assertThat(awaitItem().totalsVisible).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial search text is empty`() = runTest {
        viewModel.searchText.test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // Order List Loading Tests
    // ========================================

    @Test
    fun `orders list loads from repository`() = runTest {
        // Arrange
        val order1 = TestFixtures.createOrder1()
        val order2 = TestFixtures.createOrder2Return()
        val order3 = TestFixtures.createOrder3Sent()
        orderRepository.addOrder(order1)
        orderRepository.addOrder(order2)
        orderRepository.addOrder(order3)

        // Act & Assert
        viewModel.documentsFlow.test {
            val orders = awaitItem()
            assertThat(orders).hasSize(3)
            assertThat(orders.map { it.guid }).containsExactly(
                order1.guid, order2.guid, order3.guid
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `orders are filtered by current account`() = runTest {
        // Arrange
        val order1 = TestFixtures.createOrder1()
        val order2 = TestFixtures.createOrder1().copy(
            guid = "different-order",
            databaseId = "different-account"
        )
        orderRepository.addOrder(order1)
        orderRepository.addOrder(order2)

        // Act & Assert
        viewModel.documentsFlow.test {
            val orders = awaitItem()
            assertThat(orders).hasSize(1)
            assertThat(orders[0].databaseId).isEqualTo(FakeUserAccountRepository.TEST_ACCOUNT_GUID)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `orders list updates when new order added`() = runTest {
        viewModel.documentsFlow.test {
            // Initial empty list
            assertThat(awaitItem()).isEmpty()

            // Add order
            val order = TestFixtures.createOrder1()
            orderRepository.addOrder(order)

            // Should receive updated list
            val orders = awaitItem()
            assertThat(orders).hasSize(1)
            assertThat(orders[0].guid).isEqualTo(order.guid)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `orders list updates when order deleted`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        orderRepository.addOrder(order)

        viewModel.documentsFlow.test {
            // Initial list with one order
            assertThat(awaitItem()).hasSize(1)

            // Delete order
            orderRepository.deleteDocument(order)

            // Should receive empty list
            assertThat(awaitItem()).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // Search Text Filtering Tests
    // ========================================

    @Test
    fun `setSearchText filters orders by client description`() = runTest {
        // Arrange
        val order1 = TestFixtures.createOrder1() // ABC Retail Store
        val order2 = TestFixtures.createOrder2Return() // XYZ Supermarket
        orderRepository.addOrder(order1)
        orderRepository.addOrder(order2)

        viewModel.documentsFlow.test {
            // Initial: both orders
            assertThat(awaitItem()).hasSize(2)

            // Act: filter by "ABC"
            viewModel.setSearchText("ABC")

            // Assert: only ABC order
            val filtered = awaitItem()
            assertThat(filtered).hasSize(1)
            assertThat(filtered[0].clientDescription).contains("ABC")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setSearchText with empty string shows all orders`() = runTest {
        // Arrange
        val order1 = TestFixtures.createOrder1()
        val order2 = TestFixtures.createOrder2Return()
        orderRepository.addOrder(order1)
        orderRepository.addOrder(order2)

        viewModel.documentsFlow.test {
            assertThat(awaitItem()).hasSize(2)

            // Filter
            viewModel.setSearchText("ABC")
            assertThat(awaitItem()).hasSize(1)

            // Clear filter
            viewModel.setSearchText("")
            assertThat(awaitItem()).hasSize(2)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setSearchText is case insensitive`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1().copy(clientDescription = "ABC Store")
        orderRepository.addOrder(order)

        viewModel.documentsFlow.test {
            assertThat(awaitItem()).hasSize(1)

            // Lowercase search
            viewModel.setSearchText("abc")
            assertThat(awaitItem()).hasSize(1)

            // Uppercase search
            viewModel.setSearchText("STORE")
            assertThat(awaitItem()).hasSize(1)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setSearchText with no matches returns empty list`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        orderRepository.addOrder(order)

        viewModel.documentsFlow.test {
            assertThat(awaitItem()).hasSize(1)

            // Search for non-existent text
            viewModel.setSearchText("NONEXISTENT")

            assertThat(awaitItem()).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // Date Filtering Tests
    // ========================================

    @Test
    fun `setDate filters orders by date`() = runTest {
        // Arrange
        val calendar = Calendar.getInstance()
        calendar.set(2025, Calendar.JANUARY, 15)
        val targetDate = calendar.time

        val matchingOrder = TestFixtures.createOrder1().copy(
            guid = "matching-order",
            date = "2025-01-15"
        )
        val differentOrder = TestFixtures.createOrder1().copy(
            guid = "different-order",
            date = "2025-01-20"
        )

        orderRepository.addOrder(matchingOrder)
        orderRepository.addOrder(differentOrder)

        viewModel.documentsFlow.test {
            assertThat(awaitItem()).hasSize(2)

            // Act: filter by Jan 15
            viewModel.setDate(targetDate)

            // Assert: only matching order
            val filtered = awaitItem()
            assertThat(filtered).hasSize(1)
            assertThat(filtered[0].guid).isEqualTo(matchingOrder.guid)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearDateFilter removes date filtering`() = runTest {
        // Arrange
        val calendar = Calendar.getInstance()
        val targetDate = calendar.apply { set(2025, Calendar.JANUARY, 15) }.time

        val order1 = TestFixtures.createOrder1().copy(
            guid = "order-1",
            date = "2025-01-15"
        )
        val order2 = TestFixtures.createOrder1().copy(
            guid = "order-2",
            date = "2025-01-20"
        )

        orderRepository.addOrder(order1)
        orderRepository.addOrder(order2)

        viewModel.documentsFlow.test {
            assertThat(awaitItem()).hasSize(2)

            // Apply filter
            viewModel.setDate(targetDate)
            assertThat(awaitItem()).hasSize(1)

            // Clear filter
            viewModel.setDate(null)
            assertThat(awaitItem()).hasSize(2)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // Document Totals Tests
    // ========================================

    @Test
    fun `documentTotalsFlow calculates order totals`() = runTest {
        // Arrange
        val order1 = TestFixtures.createOrder1().copy(
            price = 1000.0,
            quantity = 10.0
        )
        val order2 = TestFixtures.createOrder2Return().copy(
            price = 500.0,
            quantity = 5.0
        )

        orderRepository.addOrder(order1)
        orderRepository.addOrder(order2)

        // Act & Assert
        viewModel.totalsFlow.test {
            val totals = awaitItem()
            val totalSum = totals.sumOf { it.sum }
            val totalQuantity = totals.sumOf { it.quantity }
            assertThat(totalSum).isEqualTo(1500.0) // 1000 + 500
            assertThat(totalQuantity).isEqualTo(15.0) // 10 + 5
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `documentTotalsFlow updates when orders change`() = runTest {
        viewModel.totalsFlow.test {
            // Initial: zero totals
            var totals = awaitItem()
            assertThat(totals).isEmpty()

            // Add order
            val order = TestFixtures.createOrder1().copy(price = 1000.0, quantity = 10.0)
            orderRepository.addOrder(order)

            // Updated totals
            totals = awaitItem()
            val totalSum = totals.sumOf { it.sum }
            val totalQuantity = totals.sumOf { it.quantity }
            assertThat(totalSum).isEqualTo(1000.0)
            assertThat(totalQuantity).isEqualTo(10.0)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `documentTotalsFlow respects date filters`() = runTest {
        // Arrange
        val calendar = Calendar.getInstance()

        val order1 = TestFixtures.createOrder1().copy(
            guid = "order-old",
            date = "2025-01-10",
            price = 1000.0
        )
        val order2 = TestFixtures.createOrder1().copy(
            guid = "order-new",
            date = "2025-01-20",
            price = 500.0
        )

        orderRepository.addOrder(order1)
        orderRepository.addOrder(order2)

        viewModel.totalsFlow.test {
            // Initial: both orders
            var totals = awaitItem()
            var totalSum = totals.sumOf { it.sum }
            assertThat(totalSum).isEqualTo(1500.0)

            // Filter from Jan 15
            val dateFrom = calendar.apply { set(2025, Calendar.JANUARY, 15) }.time
            viewModel.setDate(dateFrom)

            // Only new order counted
            totals = awaitItem()
            totalSum = totals.sumOf { it.sum }
            assertThat(totalSum).isEqualTo(500.0)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setTotalsVisible toggles totals visibility`() = runTest {
        viewModel.uiState.test {
            // Initial: true
            assertThat(awaitItem().totalsVisible).isTrue()

            // Toggle off
            viewModel.setTotalsVisible(false)
            assertThat(awaitItem().totalsVisible).isFalse()

            // Toggle on
            viewModel.setTotalsVisible(true)
            assertThat(awaitItem().totalsVisible).isTrue()

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // Copy Order Functionality Tests
    // ========================================

    @Test
    fun `copyDocument calls CopyOrderUseCase on success`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        val copiedOrder = order.copy(guid = "copied-order-guid")

        whenever(copyOrderUseCase.invoke(order)) doReturn Result.Success(copiedOrder)

        var resultGuid = ""

        // Act
        viewModel.copyDocument(order) { resultGuid = it }
        advanceUntilIdle()

        // Assert
        verify(copyOrderUseCase).invoke(order)
        assertThat(resultGuid).isEqualTo("copied-order-guid")
    }

    @Test
    fun `copyDocument returns empty string on error`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        whenever(copyOrderUseCase.invoke(order)) doReturn Result.Error(
            DomainException.DatabaseError("Copy failed")
        )

        var resultGuid = "not-empty"

        // Act
        viewModel.copyDocument(order) { resultGuid = it }
        advanceUntilIdle()

        // Assert
        assertThat(resultGuid).isEmpty()
    }

    @Test
    fun `copyDocument handles multiple copy operations`() = runTest {
        // Arrange
        val order1 = TestFixtures.createOrder1()
        val order2 = TestFixtures.createOrder2Return()

        val copiedOrder1 = order1.copy(guid = "copy-1")
        val copiedOrder2 = order2.copy(guid = "copy-2")

        whenever(copyOrderUseCase.invoke(order1)) doReturn Result.Success(copiedOrder1)
        whenever(copyOrderUseCase.invoke(order2)) doReturn Result.Success(copiedOrder2)

        val results = mutableListOf<String>()

        // Act
        viewModel.copyDocument(order1) { results.add(it) }
        viewModel.copyDocument(order2) { results.add(it) }
        advanceUntilIdle()

        // Assert
        assertThat(results).containsExactly("copy-1", "copy-2")
    }

    // ========================================
    // Combined Filtering Tests
    // ========================================

    @Test
    fun `search text and date filters work together`() = runTest {
        // Arrange
        val calendar = Calendar.getInstance()

        val order1 = TestFixtures.createOrder1().copy(
            guid = "abc-old",
            clientDescription = "ABC Store",
            date = "2025-01-10"
        )
        val order2 = TestFixtures.createOrder1().copy(
            guid = "abc-new",
            clientDescription = "ABC Market",
            date = "2025-01-20"
        )
        val order3 = TestFixtures.createOrder1().copy(
            guid = "xyz-new",
            clientDescription = "XYZ Store",
            date = "2025-01-20"
        )

        orderRepository.addOrder(order1)
        orderRepository.addOrder(order2)
        orderRepository.addOrder(order3)

        viewModel.documentsFlow.test {
            assertThat(awaitItem()).hasSize(3)

            // Filter by text
            viewModel.setSearchText("ABC")
            assertThat(awaitItem()).hasSize(2)

            // Also filter by date
            val dateFrom = calendar.apply { set(2025, Calendar.JANUARY, 15) }.time
            viewModel.setDate(dateFrom)

            // Only ABC new order
            val filtered = awaitItem()
            assertThat(filtered).hasSize(1)
            assertThat(filtered[0].guid).isEqualTo("abc-new")

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // Edge Cases and Error Handling Tests
    // ========================================

    @Test
    fun `empty repository returns empty list`() = runTest {
        viewModel.documentsFlow.test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filtering with special characters works correctly`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1().copy(
            clientDescription = "Store & Co. (Main)"
        )
        orderRepository.addOrder(order)

        viewModel.documentsFlow.test {
            assertThat(awaitItem()).hasSize(1)

            // Search with special characters
            viewModel.setSearchText("& Co.")
            assertThat(awaitItem()).hasSize(1)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `very long search text filters correctly`() = runTest {
        // Arrange
        val longText = "A".repeat(1000)
        val order = TestFixtures.createOrder1().copy(clientDescription = longText)
        orderRepository.addOrder(order)

        viewModel.documentsFlow.test {
            assertThat(awaitItem()).hasSize(1)

            viewModel.setSearchText(longText.substring(0, 500))
            assertThat(awaitItem()).hasSize(1)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `multiple rapid filter changes emit correct results`() = runTest {
        // Arrange
        val order1 = TestFixtures.createOrder1().copy(clientDescription = "AAA")
        val order2 = TestFixtures.createOrder1().copy(
            guid = "order-2",
            clientDescription = "BBB"
        )

        orderRepository.addOrder(order1)
        orderRepository.addOrder(order2)

        viewModel.documentsFlow.test {
            assertThat(awaitItem()).hasSize(2)

            // Rapid changes
            viewModel.setSearchText("AAA")
            viewModel.setSearchText("BBB")
            viewModel.setSearchText("")

            // Should end with all orders
            val final = awaitItem()
            assertThat(final).hasSize(2)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `account switch updates order list`() = runTest {
        // Arrange
        val order1 = TestFixtures.createOrder1()
        orderRepository.addOrder(order1)

        viewModel.documentsFlow.test {
            assertThat(awaitItem()).hasSize(1)

            // Switch account
            userAccountRepository.setIsCurrent("different-account")

            // Should show empty list for new account
            assertThat(awaitItem()).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `totals are zero for empty list`() = runTest {
        viewModel.totalsFlow.test {
            val totals = awaitItem()
            assertThat(totals).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loading large number of orders performs correctly`() = runTest {
        // Arrange: Add 100 orders
        repeat(100) { index ->
            val order = TestFixtures.createOrder1().copy(
                guid = "order-$index",
                price = index.toDouble(),
                quantity = 1.0
            )
            orderRepository.addOrder(order)
        }

        // Act & Assert
        viewModel.documentsFlow.test {
            val orders = awaitItem()
            assertThat(orders).hasSize(100)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.totalsFlow.test {
            val totals = awaitItem()
            val totalQuantity = totals.sumOf { it.quantity }
            assertThat(totalQuantity).isEqualTo(100.0)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
