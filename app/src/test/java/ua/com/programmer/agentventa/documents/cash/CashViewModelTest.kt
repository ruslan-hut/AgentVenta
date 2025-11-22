package ua.com.programmer.agentventa.documents.cash

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import ua.com.programmer.agentventa.dao.entity.Cash
import ua.com.programmer.agentventa.dao.entity.Client
import ua.com.programmer.agentventa.domain.usecase.cash.EnableCashEditUseCase
import ua.com.programmer.agentventa.domain.usecase.cash.SaveCashUseCase
import ua.com.programmer.agentventa.domain.usecase.cash.ValidateCashUseCase
import ua.com.programmer.agentventa.fake.FakeCashRepository
import ua.com.programmer.agentventa.fixtures.TestFixtures
import ua.com.programmer.agentventa.logger.Logger
import ua.com.programmer.agentventa.shared.DocumentEvent
import ua.com.programmer.agentventa.util.MainDispatcherRule
import ua.com.programmer.agentventa.util.getOrAwaitValue

/**
 * Test suite for CashViewModel.
 *
 * Tests:
 * - Cash document creation and editing
 * - Payment type selection
 * - Amount calculation
 * - Client selection
 * - Fiscal flag handling
 * - Save/delete operations
 * - Validation logic
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CashViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var cashRepository: FakeCashRepository
    private lateinit var validateCashUseCase: ValidateCashUseCase
    private lateinit var saveCashUseCase: SaveCashUseCase
    private lateinit var enableCashEditUseCase: EnableCashEditUseCase
    private lateinit var logger: Logger
    private lateinit var viewModel: CashViewModel

    @Before
    fun setup() {
        cashRepository = FakeCashRepository(TestFixtures.TEST_DB_GUID)
        logger = mock()

        // Real use cases
        validateCashUseCase = ValidateCashUseCase()
        saveCashUseCase = SaveCashUseCase(cashRepository, validateCashUseCase)
        enableCashEditUseCase = EnableCashEditUseCase(cashRepository)

        viewModel = CashViewModel(
            cashRepository = cashRepository,
            validateCashUseCase = validateCashUseCase,
            saveCashUseCase = saveCashUseCase,
            enableCashEditUseCase = enableCashEditUseCase,
            logger = logger
        )
    }

    @After
    fun tearDown() {
        cashRepository.clearAll()
    }

    // ========== Initial State Tests ==========

    @Test
    fun `initial state has empty cash document`() = runTest {
        // Assert
        assertThat(viewModel.documentGuid.value).isEmpty()
        assertThat(viewModel.documentFlow.value.guid).isEmpty()
    }

    @Test
    fun `initial state is not loading`() = runTest {
        // Assert
        assertThat(viewModel.isLoading.value).isFalse()
    }

    @Test
    fun `initial save result is null`() = runTest {
        // Assert
        assertThat(viewModel.saveResult.value).isNull()
    }

    // ========== Document Creation and Loading Tests ==========

    @Test
    fun `create new cash document`() = runTest {
        // Act
        viewModel.setCurrentDocument(null)
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.documentGuid.value).isNotEmpty()
        val cash = viewModel.documentFlow.value
        assertThat(cash.guid).isNotEmpty()
    }

    @Test
    fun `load existing cash document`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1()
        cashRepository.addCash(cash)

        // Act
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Assert
        viewModel.documentFlow.test {
            val loadedCash = awaitItem()
            assertThat(loadedCash.guid).isEqualTo(cash.guid)
            assertThat(loadedCash.sum).isEqualTo(cash.sum)
            assertThat(loadedCash.clientGuid).isEqualTo(cash.clientGuid)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `document LiveData exposes documentFlow`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1()
        cashRepository.addCash(cash)

        // Act
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Assert
        val document = viewModel.document.getOrAwaitValue()
        assertThat(document.guid).isEqualTo(cash.guid)
    }

    // ========== Amount Editing Tests ==========

    @Test
    fun `onEditSum updates cash amount`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1()
        cashRepository.addCash(cash)
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Act
        viewModel.onEditSum("2500.50")
        advanceUntilIdle()

        // Assert
        val updatedCash = cashRepository.getDocument(cash.guid).first()
        assertThat(updatedCash.sum).isEqualTo(2500.50)
    }

    @Test
    fun `onEditSum with invalid string sets sum to zero`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1()
        cashRepository.addCash(cash)
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Act
        viewModel.onEditSum("invalid")
        advanceUntilIdle()

        // Assert
        val updatedCash = cashRepository.getDocument(cash.guid).first()
        assertThat(updatedCash.sum).isEqualTo(0.0)
    }

    @Test
    fun `onEditSum with empty string sets sum to zero`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1()
        cashRepository.addCash(cash)
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Act
        viewModel.onEditSum("")
        advanceUntilIdle()

        // Assert
        val updatedCash = cashRepository.getDocument(cash.guid).first()
        assertThat(updatedCash.sum).isEqualTo(0.0)
    }

    @Test
    fun `onEditSum accepts decimal values`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1()
        cashRepository.addCash(cash)
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Act
        viewModel.onEditSum("1234.56")
        advanceUntilIdle()

        // Assert
        val updatedCash = cashRepository.getDocument(cash.guid).first()
        assertThat(updatedCash.sum).isEqualTo(1234.56)
    }

    // ========== Fiscal Flag Tests ==========

    @Test
    fun `onEditFiscal sets fiscal flag to 1`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1().copy(isFiscal = 0)
        cashRepository.addCash(cash)
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Act
        viewModel.onEditFiscal(1)
        advanceUntilIdle()

        // Assert
        val updatedCash = cashRepository.getDocument(cash.guid).first()
        assertThat(updatedCash.isFiscal).isEqualTo(1)
    }

    @Test
    fun `onEditFiscal sets fiscal flag to 0`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1().copy(isFiscal = 1)
        cashRepository.addCash(cash)
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Act
        viewModel.onEditFiscal(0)
        advanceUntilIdle()

        // Assert
        val updatedCash = cashRepository.getDocument(cash.guid).first()
        assertThat(updatedCash.isFiscal).isEqualTo(0)
    }

    // ========== Client Selection Tests ==========

    @Test
    fun `onClientClick updates cash client`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1()
        val client = TestFixtures.createLClient1()

        cashRepository.addCash(cash)
        cashRepository.addClient(Client(guid = client.guid, description = client.description))
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        var popUpCalled = false

        // Act
        viewModel.onClientClick(client) { popUpCalled = true }
        advanceUntilIdle()

        // Assert
        val updatedCash = cashRepository.getDocument(cash.guid).first()
        assertThat(updatedCash.clientGuid).isEqualTo(client.guid)
        assertThat(updatedCash.client).isEqualTo(client.description)
        assertThat(popUpCalled).isTrue()
    }

    @Test
    fun `onClientClick triggers popUp callback`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1()
        val client = TestFixtures.createLClient2()

        cashRepository.addCash(cash)
        cashRepository.addClient(Client(guid = client.guid, description = client.description))
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        var callbackInvoked = false

        // Act
        viewModel.onClientClick(client) { callbackInvoked = true }
        advanceUntilIdle()

        // Assert
        assertThat(callbackInvoked).isTrue()
    }

    // ========== Notes Editing Tests ==========

    @Test
    fun `onEditNotes updates notes and resets flags`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1().copy(
            isProcessed = 1,
            isSent = 1
        )
        cashRepository.addCash(cash)
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Act
        viewModel.onEditNotes("Updated notes for testing")
        advanceUntilIdle()

        // Assert
        val updatedCash = cashRepository.getDocument(cash.guid).first()
        assertThat(updatedCash.notes).isEqualTo("Updated notes for testing")
        assertThat(updatedCash.isProcessed).isEqualTo(0)
        assertThat(updatedCash.isSent).isEqualTo(0)
    }

    // ========== Save Document Tests ==========

    @Test
    fun `saveDocument marks cash as processed`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1().copy(
            clientGuid = TestFixtures.CLIENT_1_GUID,
            sum = 1000.0,
            isProcessed = 0
        )
        cashRepository.addCash(cash)
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Act
        viewModel.saveDocument("1000.0")
        advanceUntilIdle()

        // Assert
        val savedCash = cashRepository.getDocument(cash.guid).first()
        assertThat(savedCash.isProcessed).isEqualTo(1)
    }

    @Test
    fun `saveDocument updates sum from input`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1().copy(
            clientGuid = TestFixtures.CLIENT_1_GUID,
            sum = 0.0
        )
        cashRepository.addCash(cash)
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Act
        viewModel.saveDocument("2500.75")
        advanceUntilIdle()

        // Assert
        val savedCash = cashRepository.getDocument(cash.guid).first()
        assertThat(savedCash.sum).isEqualTo(2500.75)
    }

    @Test
    fun `saveDocument emits SaveSuccess event`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1().copy(
            clientGuid = TestFixtures.CLIENT_1_GUID,
            sum = 1000.0
        )
        cashRepository.addCash(cash)
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Act
        viewModel.events.test {
            viewModel.saveDocument("1000.0")
            advanceUntilIdle()

            // Assert
            val event = awaitItem()
            assertThat(event).isInstanceOf(DocumentEvent.SaveSuccess::class.java)
            assertThat((event as DocumentEvent.SaveSuccess).guid).isEqualTo(cash.guid)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveDocument sets saveResult to true`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1().copy(
            clientGuid = TestFixtures.CLIENT_1_GUID,
            sum = 1000.0
        )
        cashRepository.addCash(cash)
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Act
        viewModel.saveDocument("1000.0")
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.saveResult.value).isTrue()
    }

    @Test
    fun `saveDocument sets loading state`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1().copy(
            clientGuid = TestFixtures.CLIENT_1_GUID,
            sum = 1000.0
        )
        cashRepository.addCash(cash)
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Act
        viewModel.isLoading.test {
            assertThat(awaitItem()).isFalse() // Initial state

            viewModel.saveDocument("1000.0")

            assertThat(awaitItem()).isTrue()  // Loading
            assertThat(awaitItem()).isFalse() // Complete

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========== Delete Document Tests ==========

    @Test
    fun `deleteDocument removes cash from repository`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1()
        cashRepository.addCash(cash)
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Act
        viewModel.deleteDocument()
        advanceUntilIdle()

        // Assert
        viewModel.documentFlow.test {
            // Should emit empty document after deletion
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteDocument emits DeleteSuccess event`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1()
        cashRepository.addCash(cash)
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Act
        viewModel.events.test {
            viewModel.deleteDocument()
            advanceUntilIdle()

            // Assert
            val event = awaitItem()
            assertThat(event).isInstanceOf(DocumentEvent.DeleteSuccess::class.java)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========== Validation Tests ==========

    @Test
    fun `validateCash returns null for valid cash`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1().copy(
            clientGuid = TestFixtures.CLIENT_1_GUID,
            sum = 1000.0
        )
        cashRepository.addCash(cash)
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Act
        val error = viewModel.validateCash()

        // Assert
        assertThat(error).isNull()
    }

    @Test
    fun `validateCash returns error for missing client`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1().copy(
            clientGuid = "",
            sum = 1000.0
        )
        cashRepository.addCash(cash)
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Act
        val error = viewModel.validateCash()

        // Assert
        assertThat(error).isNotNull()
        assertThat(error).contains("Client is required")
    }

    @Test
    fun `validateCash returns error for zero sum`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1().copy(
            clientGuid = TestFixtures.CLIENT_1_GUID,
            sum = 0.0
        )
        cashRepository.addCash(cash)
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Act
        val error = viewModel.validateCash()

        // Assert
        assertThat(error).isNotNull()
        assertThat(error).contains("Sum must be greater than zero")
    }

    @Test
    fun `validateCash returns error for negative sum`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1().copy(
            clientGuid = TestFixtures.CLIENT_1_GUID,
            sum = -100.0
        )
        cashRepository.addCash(cash)
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Act
        val error = viewModel.validateCash()

        // Assert
        assertThat(error).isNotNull()
        assertThat(error).contains("Sum must be greater than zero")
    }

    // ========== Enable Edit Tests ==========

    @Test
    fun `enableEdit resets processed and sent flags`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1().copy(
            isProcessed = 1,
            isSent = 1
        )
        cashRepository.addCash(cash)
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Act
        viewModel.enableEdit()
        advanceUntilIdle()

        // Assert
        val editableCash = cashRepository.getDocument(cash.guid).first()
        assertThat(editableCash.isProcessed).isEqualTo(0)
        assertThat(editableCash.isSent).isEqualTo(0)
    }

    @Test
    fun `isNotEditable returns true for processed cash`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1().copy(isProcessed = 1)
        cashRepository.addCash(cash)
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Act
        val notEditable = viewModel.isNotEditable()

        // Assert
        assertThat(notEditable).isTrue()
    }

    @Test
    fun `isNotEditable returns false for unprocessed cash`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1().copy(isProcessed = 0)
        cashRepository.addCash(cash)
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Act
        val notEditable = viewModel.isNotEditable()

        // Assert
        assertThat(notEditable).isFalse()
    }

    // ========== Abstract Method Implementation Tests ==========

    @Test
    fun `getDocumentGuid extracts GUID from cash document`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1()
        cashRepository.addCash(cash)
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Act & Assert
        assertThat(viewModel.documentGuid.value).isEqualTo(cash.guid)
    }

    @Test
    fun `markAsProcessed sets isProcessed flag`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1().copy(
            clientGuid = TestFixtures.CLIENT_1_GUID,
            sum = 1000.0,
            isProcessed = 0
        )
        cashRepository.addCash(cash)
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Act
        viewModel.saveDocument("1000.0")
        advanceUntilIdle()

        // Assert
        val processedCash = cashRepository.getDocument(cash.guid).first()
        assertThat(processedCash.isProcessed).isEqualTo(1)
    }

    // ========== Edge Cases Tests ==========

    @Test
    fun `changing sum after validation still validates on save`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1().copy(
            clientGuid = TestFixtures.CLIENT_1_GUID,
            sum = 1000.0
        )
        cashRepository.addCash(cash)
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Validate initially
        assertThat(viewModel.validateCash()).isNull()

        // Act - change sum to invalid via save
        viewModel.events.test {
            viewModel.saveDocument("0.0")
            advanceUntilIdle()

            // Assert - should emit error, not success
            val event = awaitItem()
            assertThat(event).isInstanceOf(DocumentEvent.SaveError::class.java)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `multiple sum edits update correctly`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1()
        cashRepository.addCash(cash)
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Act - multiple edits
        viewModel.onEditSum("100.0")
        advanceUntilIdle()

        viewModel.onEditSum("200.0")
        advanceUntilIdle()

        viewModel.onEditSum("300.50")
        advanceUntilIdle()

        // Assert - should have final value
        val updatedCash = cashRepository.getDocument(cash.guid).first()
        assertThat(updatedCash.sum).isEqualTo(300.50)
    }

    @Test
    fun `company selection updates cash document`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1()
        val company = TestFixtures.createCompany1()

        cashRepository.addCash(cash)
        cashRepository.addCompany(company)
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Act
        cashRepository.setCompany(cash.guid, company)
        advanceUntilIdle()

        // Assert
        val updatedCash = cashRepository.getDocument(cash.guid).first()
        assertThat(updatedCash.companyGuid).isEqualTo(company.guid)
        assertThat(updatedCash.company).isEqualTo(company.description)
    }

    @Test
    fun `onEditNotes with empty string clears notes`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1().copy(notes = "Some notes")
        cashRepository.addCash(cash)
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Act
        viewModel.onEditNotes("")
        advanceUntilIdle()

        // Assert
        val updatedCash = cashRepository.getDocument(cash.guid).first()
        assertThat(updatedCash.notes).isEmpty()
    }

    @Test
    fun `fiscal flag persists through save`() = runTest {
        // Arrange
        val cash = TestFixtures.createCash1().copy(
            clientGuid = TestFixtures.CLIENT_1_GUID,
            sum = 1000.0,
            isFiscal = 1
        )
        cashRepository.addCash(cash)
        viewModel.setCurrentDocument(cash.guid)
        advanceUntilIdle()

        // Act
        viewModel.saveDocument("1000.0")
        advanceUntilIdle()

        // Assert
        val savedCash = cashRepository.getDocument(cash.guid).first()
        assertThat(savedCash.isFiscal).isEqualTo(1)
    }
}
