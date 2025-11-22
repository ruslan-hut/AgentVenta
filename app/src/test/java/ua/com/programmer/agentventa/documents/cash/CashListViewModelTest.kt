package ua.com.programmer.agentventa.documents.cash

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import ua.com.programmer.agentventa.fake.FakeCashRepository
import ua.com.programmer.agentventa.fake.FakeUserAccountRepository
import ua.com.programmer.agentventa.fixtures.TestFixtures
import ua.com.programmer.agentventa.util.MainDispatcherRule
import java.util.Calendar

/**
 * Test suite for CashListViewModel
 *
 * Covers:
 * - Cash document list loading and filtering
 * - Date range filtering
 * - Search text filtering
 * - Document totals calculation
 * - Totals visibility
 * - Current account integration
 * - Edge cases and error handling
 */
class CashListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var cashRepository: FakeCashRepository
    private lateinit var userAccountRepository: FakeUserAccountRepository
    private lateinit var viewModel: CashListViewModel

    @Before
    fun setup() {
        cashRepository = FakeCashRepository(FakeUserAccountRepository.TEST_ACCOUNT_GUID)
        userAccountRepository = FakeUserAccountRepository()
        userAccountRepository.setupTestAccount()

        viewModel = CashListViewModel(
            cashRepository = cashRepository,
            userAccountRepository = userAccountRepository
        )
    }

    // ========================================
    // Initial State Tests
    // ========================================

    @Test
    fun `initial state has empty cash list`() = runTest {
        viewModel.documentsFlow.test {
            val cashList = awaitItem()
            assertThat(cashList).isEmpty()
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
    // Cash List Loading Tests
    // ========================================

    @Test
    fun `cash list loads from repository`() = runTest {
        // Arrange
        val cash1 = TestFixtures.createCash1()
        val cash2 = TestFixtures.createCash2()
        val cash3 = TestFixtures.createCash3Fiscal()
        cashRepository.addCash(cash1)
        cashRepository.addCash(cash2)
        cashRepository.addCash(cash3)

        // Act & Assert
        viewModel.documentsFlow.test {
            val cashList = awaitItem()
            assertThat(cashList).hasSize(3)
            assertThat(cashList.map { it.guid }).containsExactly(
                cash1.guid, cash2.guid, cash3.guid
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cash documents are filtered by current account`() = runTest {
        // Arrange
        val cash1 = TestFixtures.createCash1()
        val cash2 = TestFixtures.createCash1().copy(
            guid = "different-cash",
            db_guid = "different-account"
        )
        cashRepository.addCash(cash1)
        cashRepository.addCash(cash2)

        // Act & Assert
        viewModel.documentsFlow.test {
            val cashList = awaitItem()
            assertThat(cashList).hasSize(1)
            assertThat(cashList[0].db_guid).isEqualTo(FakeUserAccountRepository.TEST_ACCOUNT_GUID)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cash list updates when new document added`() = runTest {
        viewModel.documentsFlow.test {
            // Initial empty list
            assertThat(awaitItem()).isEmpty()

            // Add cash document
            val cash = TestFixtures.createCash1()
            cashRepository.addCash(cash)

            // Should receive updated list
            val cashList = awaitItem()
            assertThat(cashList).hasSize(1)
            assertThat(cashList[0].guid).isEqualTo(cash.guid)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cash list updates when document deleted`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1()
        cashRepository.addCash(cash)

        viewModel.documentsFlow.test {
            // Initial list with one document
            assertThat(awaitItem()).hasSize(1)

            // Delete document
            cashRepository.deleteDocument(cash)

            // Should receive empty list
            assertThat(awaitItem()).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // Search Text Filtering Tests
    // ========================================

    @Test
    fun `setSearchText filters cash by client description`() = runTest {
        // Arrange
        val cash1 = TestFixtures.createCash1() // Client 1
        val cash2 = TestFixtures.createCash2() // Client 2
        cashRepository.addCash(cash1)
        cashRepository.addCash(cash2)

        viewModel.documentsFlow.test {
            // Initial: both documents
            assertThat(awaitItem()).hasSize(2)

            // Act: filter by client name
            viewModel.setSearchText(cash1.clientDescription.substring(0, 5))

            // Assert: only matching cash
            val filtered = awaitItem()
            assertThat(filtered).hasSize(1)
            assertThat(filtered[0].clientDescription).contains(
                cash1.clientDescription.substring(0, 5)
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setSearchText with empty string shows all cash documents`() = runTest {
        // Arrange
        val cash1 = TestFixtures.createCash1()
        val cash2 = TestFixtures.createCash2()
        cashRepository.addCash(cash1)
        cashRepository.addCash(cash2)

        viewModel.documentsFlow.test {
            assertThat(awaitItem()).hasSize(2)

            // Filter
            viewModel.setSearchText("Client")
            assertThat(awaitItem()).hasSize(2)

            // Clear filter
            viewModel.setSearchText("")
            assertThat(awaitItem()).hasSize(2)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setSearchText is case insensitive`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1().copy(clientDescription = "ABC Client")
        cashRepository.addCash(cash)

        viewModel.documentsFlow.test {
            assertThat(awaitItem()).hasSize(1)

            // Lowercase search
            viewModel.setSearchText("abc")
            assertThat(awaitItem()).hasSize(1)

            // Uppercase search
            viewModel.setSearchText("CLIENT")
            assertThat(awaitItem()).hasSize(1)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setSearchText with no matches returns empty list`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1()
        cashRepository.addCash(cash)

        viewModel.documentsFlow.test {
            assertThat(awaitItem()).hasSize(1)

            // Search for non-existent text
            viewModel.setSearchText("NONEXISTENT")

            assertThat(awaitItem()).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setSearchText filters by notes content`() = runTest {
        // Arrange
        val cash1 = TestFixtures.createCash1().copy(notes = "Payment for invoice 123")
        val cash2 = TestFixtures.createCash2().copy(notes = "Advance payment")
        cashRepository.addCash(cash1)
        cashRepository.addCash(cash2)

        viewModel.documentsFlow.test {
            assertThat(awaitItem()).hasSize(2)

            // Filter by notes
            viewModel.setSearchText("invoice")

            val filtered = awaitItem()
            assertThat(filtered).hasSize(1)
            assertThat(filtered[0].notes).contains("invoice")

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // Date Filtering Tests
    // ========================================

    @Test
    fun `setDateFrom filters cash by start date`() = runTest {
        // Arrange
        val calendar = Calendar.getInstance()
        calendar.set(2025, Calendar.JANUARY, 15)
        val dateFrom = calendar.time

        val oldCash = TestFixtures.createCash1().copy(
            guid = "old-cash",
            date = calendar.apply { set(2025, Calendar.JANUARY, 10) }.time
        )
        val newCash = TestFixtures.createCash1().copy(
            guid = "new-cash",
            date = calendar.apply { set(2025, Calendar.JANUARY, 20) }.time
        )

        cashRepository.addCash(oldCash)
        cashRepository.addCash(newCash)

        viewModel.documentsFlow.test {
            assertThat(awaitItem()).hasSize(2)

            // Act: filter from Jan 15
            viewModel.setDateFrom(dateFrom)

            // Assert: only new cash
            val filtered = awaitItem()
            assertThat(filtered).hasSize(1)
            assertThat(filtered[0].guid).isEqualTo(newCash.guid)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setDateTo filters cash by end date`() = runTest {
        // Arrange
        val calendar = Calendar.getInstance()
        calendar.set(2025, Calendar.JANUARY, 15)
        val dateTo = calendar.time

        val oldCash = TestFixtures.createCash1().copy(
            guid = "old-cash",
            date = calendar.apply { set(2025, Calendar.JANUARY, 10) }.time
        )
        val newCash = TestFixtures.createCash1().copy(
            guid = "new-cash",
            date = calendar.apply { set(2025, Calendar.JANUARY, 20) }.time
        )

        cashRepository.addCash(oldCash)
        cashRepository.addCash(newCash)

        viewModel.documentsFlow.test {
            assertThat(awaitItem()).hasSize(2)

            // Act: filter to Jan 15
            viewModel.setDateTo(dateTo)

            // Assert: only old cash
            val filtered = awaitItem()
            assertThat(filtered).hasSize(1)
            assertThat(filtered[0].guid).isEqualTo(oldCash.guid)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setDateFrom and setDateTo filters cash by date range`() = runTest {
        // Arrange
        val calendar = Calendar.getInstance()

        val cash1 = TestFixtures.createCash1().copy(
            guid = "cash-jan-5",
            date = calendar.apply { set(2025, Calendar.JANUARY, 5) }.time
        )
        val cash2 = TestFixtures.createCash1().copy(
            guid = "cash-jan-15",
            date = calendar.apply { set(2025, Calendar.JANUARY, 15) }.time
        )
        val cash3 = TestFixtures.createCash1().copy(
            guid = "cash-jan-25",
            date = calendar.apply { set(2025, Calendar.JANUARY, 25) }.time
        )

        cashRepository.addCash(cash1)
        cashRepository.addCash(cash2)
        cashRepository.addCash(cash3)

        viewModel.documentsFlow.test {
            assertThat(awaitItem()).hasSize(3)

            // Act: filter Jan 10 - Jan 20
            val dateFrom = calendar.apply { set(2025, Calendar.JANUARY, 10) }.time
            val dateTo = calendar.apply { set(2025, Calendar.JANUARY, 20) }.time
            viewModel.setDateFrom(dateFrom)
            viewModel.setDateTo(dateTo)

            // Assert: only middle cash
            val filtered = awaitItem()
            assertThat(filtered).hasSize(1)
            assertThat(filtered[0].guid).isEqualTo(cash2.guid)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearDateFilter removes date filtering`() = runTest {
        // Arrange
        val calendar = Calendar.getInstance()
        val dateFrom = calendar.apply { set(2025, Calendar.JANUARY, 15) }.time

        val cash1 = TestFixtures.createCash1().copy(
            guid = "cash-1",
            date = calendar.apply { set(2025, Calendar.JANUARY, 10) }.time
        )
        val cash2 = TestFixtures.createCash1().copy(
            guid = "cash-2",
            date = calendar.apply { set(2025, Calendar.JANUARY, 20) }.time
        )

        cashRepository.addCash(cash1)
        cashRepository.addCash(cash2)

        viewModel.documentsFlow.test {
            assertThat(awaitItem()).hasSize(2)

            // Apply filter
            viewModel.setDateFrom(dateFrom)
            assertThat(awaitItem()).hasSize(1)

            // Clear filter
            viewModel.clearDateFilter()
            assertThat(awaitItem()).hasSize(2)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // Document Totals Tests
    // ========================================

    @Test
    fun `documentTotalsFlow calculates cash totals`() = runTest {
        // Arrange
        val cash1 = TestFixtures.createCash1().copy(sum = 1000.0)
        val cash2 = TestFixtures.createCash2().copy(sum = 500.0)
        val cash3 = TestFixtures.createCash3Fiscal().copy(sum = 250.0)

        cashRepository.addCash(cash1)
        cashRepository.addCash(cash2)
        cashRepository.addCash(cash3)

        // Act & Assert
        viewModel.documentTotalsFlow.test {
            val totals = awaitItem()
            assertThat(totals.sum).isEqualTo(1750.0) // 1000 + 500 + 250
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `documentTotalsFlow updates when cash documents change`() = runTest {
        viewModel.documentTotalsFlow.test {
            // Initial: zero totals
            var totals = awaitItem()
            assertThat(totals.sum).isEqualTo(0.0)

            // Add cash
            val cash = TestFixtures.createCash1().copy(sum = 1000.0)
            cashRepository.addCash(cash)

            // Updated totals
            totals = awaitItem()
            assertThat(totals.sum).isEqualTo(1000.0)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `documentTotalsFlow respects date filters`() = runTest {
        // Arrange
        val calendar = Calendar.getInstance()

        val cash1 = TestFixtures.createCash1().copy(
            guid = "cash-old",
            date = calendar.apply { set(2025, Calendar.JANUARY, 10) }.time,
            sum = 1000.0
        )
        val cash2 = TestFixtures.createCash1().copy(
            guid = "cash-new",
            date = calendar.apply { set(2025, Calendar.JANUARY, 20) }.time,
            sum = 500.0
        )

        cashRepository.addCash(cash1)
        cashRepository.addCash(cash2)

        viewModel.documentTotalsFlow.test {
            // Initial: both documents
            var totals = awaitItem()
            assertThat(totals.sum).isEqualTo(1500.0)

            // Filter from Jan 15
            val dateFrom = calendar.apply { set(2025, Calendar.JANUARY, 15) }.time
            viewModel.setDateFrom(dateFrom)

            // Only new cash counted
            totals = awaitItem()
            assertThat(totals.sum).isEqualTo(500.0)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `documentTotalsFlow respects search filter`() = runTest {
        // Arrange
        val cash1 = TestFixtures.createCash1().copy(
            clientDescription = "ABC Client",
            sum = 1000.0
        )
        val cash2 = TestFixtures.createCash2().copy(
            clientDescription = "XYZ Client",
            sum = 500.0
        )

        cashRepository.addCash(cash1)
        cashRepository.addCash(cash2)

        viewModel.documentTotalsFlow.test {
            // Initial: both documents
            var totals = awaitItem()
            assertThat(totals.sum).isEqualTo(1500.0)

            // Filter by "ABC"
            viewModel.setSearchText("ABC")

            // Only ABC cash counted
            totals = awaitItem()
            assertThat(totals.sum).isEqualTo(1000.0)

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
    // Combined Filtering Tests
    // ========================================

    @Test
    fun `search text and date filters work together`() = runTest {
        // Arrange
        val calendar = Calendar.getInstance()

        val cash1 = TestFixtures.createCash1().copy(
            guid = "abc-old",
            clientDescription = "ABC Client",
            date = calendar.apply { set(2025, Calendar.JANUARY, 10) }.time
        )
        val cash2 = TestFixtures.createCash1().copy(
            guid = "abc-new",
            clientDescription = "ABC Store",
            date = calendar.apply { set(2025, Calendar.JANUARY, 20) }.time
        )
        val cash3 = TestFixtures.createCash1().copy(
            guid = "xyz-new",
            clientDescription = "XYZ Client",
            date = calendar.apply { set(2025, Calendar.JANUARY, 20) }.time
        )

        cashRepository.addCash(cash1)
        cashRepository.addCash(cash2)
        cashRepository.addCash(cash3)

        viewModel.documentsFlow.test {
            assertThat(awaitItem()).hasSize(3)

            // Filter by text
            viewModel.setSearchText("ABC")
            assertThat(awaitItem()).hasSize(2)

            // Also filter by date
            val dateFrom = calendar.apply { set(2025, Calendar.JANUARY, 15) }.time
            viewModel.setDateFrom(dateFrom)

            // Only ABC new cash
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
    fun `totals are zero for empty list`() = runTest {
        viewModel.documentTotalsFlow.test {
            val totals = awaitItem()
            assertThat(totals.sum).isEqualTo(0.0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filtering with special characters works correctly`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1().copy(
            clientDescription = "Client & Co. (Main)"
        )
        cashRepository.addCash(cash)

        viewModel.documentsFlow.test {
            assertThat(awaitItem()).hasSize(1)

            // Search with special characters
            viewModel.setSearchText("& Co.")
            assertThat(awaitItem()).hasSize(1)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `multiple rapid filter changes emit correct results`() = runTest {
        // Arrange
        val cash1 = TestFixtures.createCash1().copy(clientDescription = "AAA Client")
        val cash2 = TestFixtures.createCash1().copy(
            guid = "cash-2",
            clientDescription = "BBB Client"
        )

        cashRepository.addCash(cash1)
        cashRepository.addCash(cash2)

        viewModel.documentsFlow.test {
            assertThat(awaitItem()).hasSize(2)

            // Rapid changes
            viewModel.setSearchText("AAA")
            viewModel.setSearchText("BBB")
            viewModel.setSearchText("")

            // Should end with all cash documents
            val final = awaitItem()
            assertThat(final).hasSize(2)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `account switch updates cash list`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1()
        cashRepository.addCash(cash)

        viewModel.documentsFlow.test {
            assertThat(awaitItem()).hasSize(1)

            // Switch account
            userAccountRepository.switchAccount("different-account")

            // Should show empty list for new account
            assertThat(awaitItem()).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loading large number of cash documents performs correctly`() = runTest {
        // Arrange: Add 100 cash documents
        repeat(100) { index ->
            val cash = TestFixtures.createCash1().copy(
                guid = "cash-$index",
                sum = index.toDouble()
            )
            cashRepository.addCash(cash)
        }

        // Act & Assert
        viewModel.documentsFlow.test {
            val cashList = awaitItem()
            assertThat(cashList).hasSize(100)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.documentTotalsFlow.test {
            val totals = awaitItem()
            // Sum of 0 + 1 + 2 + ... + 99 = 4950
            assertThat(totals.sum).isEqualTo(4950.0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `fiscal and non-fiscal cash documents are both included`() = runTest {
        // Arrange
        val regularCash = TestFixtures.createCash1().copy(isFiscal = 0)
        val fiscalCash = TestFixtures.createCash3Fiscal().copy(isFiscal = 1)

        cashRepository.addCash(regularCash)
        cashRepository.addCash(fiscalCash)

        // Act & Assert
        viewModel.documentsFlow.test {
            val cashList = awaitItem()
            assertThat(cashList).hasSize(2)
            assertThat(cashList.map { it.isFiscal }).containsExactly(0, 1)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
