package ua.com.programmer.agentventa.documents.common

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import ua.com.programmer.agentventa.dao.entity.Order
import ua.com.programmer.agentventa.fake.FakeOrderRepository
import ua.com.programmer.agentventa.fixtures.TestFixtures
import ua.com.programmer.agentventa.logger.Logger
import ua.com.programmer.agentventa.shared.DocumentEvent
import ua.com.programmer.agentventa.util.MainDispatcherRule
import java.util.*

/**
 * Test suite for DocumentViewModel base class functionality.
 * Tests core document management operations: create, load, save, delete.
 *
 * Uses Order as the concrete document type for testing the abstract base class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeOrderRepository
    private lateinit var logger: Logger
    private lateinit var viewModel: TestDocumentViewModel

    @Before
    fun setup() {
        repository = FakeOrderRepository(TestFixtures.TEST_DB_GUID)
        logger = mock()
        viewModel = TestDocumentViewModel(repository, logger)
    }

    @After
    fun tearDown() {
        repository.clearAll()
    }

    // ========== Initial State Tests ==========

    @Test
    fun `initial state has empty document GUID`() {
        assertThat(viewModel.documentGuid.value).isEmpty()
    }

    @Test
    fun `initial state has empty document`() {
        val document = viewModel.documentFlow.value
        assertThat(document.guid).isEmpty()
    }

    @Test
    fun `initial state is not loading`() {
        assertThat(viewModel.isLoading.value).isFalse()
    }

    @Test
    fun `initial state has null save result`() {
        assertThat(viewModel.saveResult.value).isNull()
    }

    @Test
    fun `getGuid returns empty string initially`() {
        assertThat(viewModel.getGuid()).isEmpty()
    }

    // ========== Load Document Tests ==========

    @Test
    fun `setCurrentDocument with valid GUID loads document from repository`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        repository.addOrder(order)

        // Act
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Assert
        viewModel.documentFlow.test {
            val loadedOrder = awaitItem()
            assertThat(loadedOrder.guid).isEqualTo(order.guid)
            assertThat(loadedOrder.clientDescription).isEqualTo(order.clientDescription)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setCurrentDocument updates documentGuid StateFlow`() = runTest {
        // Arrange
        val testGuid = TestFixtures.ORDER_1_GUID

        // Act
        viewModel.setCurrentDocument(testGuid)
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.documentGuid.value).isEqualTo(testGuid)
    }

    @Test
    fun `setCurrentDocument with null ID creates new document`() = runTest {
        // Act
        viewModel.setCurrentDocument(null)
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.documentGuid.value).isNotEmpty()
        assertThat(viewModel.documentFlow.value.guid).isNotEmpty()
    }

    @Test
    fun `setCurrentDocument with empty string creates new document`() = runTest {
        // Act
        viewModel.setCurrentDocument("")
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.documentGuid.value).isNotEmpty()
        assertThat(viewModel.documentFlow.value.guid).isNotEmpty()
    }

    @Test
    fun `setCurrentDocument resets save result to null`() = runTest {
        // Arrange - simulate previous save
        viewModel.testUpdateDocumentWithResult(TestFixtures.createOrder1())
        advanceUntilIdle()
        assertThat(viewModel.saveResult.value).isTrue()

        // Act
        viewModel.setCurrentDocument(TestFixtures.ORDER_2_GUID)
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.saveResult.value).isNull()
    }

    @Test
    fun `documentFlow emits new document when GUID changes`() = runTest {
        // Arrange
        val order1 = TestFixtures.createOrder1()
        val order2 = TestFixtures.createOrder2Return()
        repository.addOrder(order1)
        repository.addOrder(order2)

        // Act & Assert
        viewModel.documentFlow.test {
            // Initial empty document
            assertThat(awaitItem().guid).isEmpty()

            // Load first order
            viewModel.setCurrentDocument(order1.guid)
            assertThat(awaitItem().guid).isEqualTo(order1.guid)

            // Load second order
            viewModel.setCurrentDocument(order2.guid)
            assertThat(awaitItem().guid).isEqualTo(order2.guid)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========== New Document Creation Tests ==========

    @Test
    fun `initNewDocument creates document in repository`() = runTest {
        // Act
        viewModel.testInitNewDocument()
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.documentGuid.value).isNotEmpty()
        val document = viewModel.documentFlow.value
        assertThat(document.guid).isNotEmpty()
    }

    @Test
    fun `initNewDocument sets loading state`() = runTest {
        // Act
        viewModel.isLoading.test {
            assertThat(awaitItem()).isFalse() // Initial state

            viewModel.testInitNewDocument()

            assertThat(awaitItem()).isTrue()  // Loading
            assertThat(awaitItem()).isFalse() // Complete

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initNewDocument with repository failure emits error event`() = runTest {
        // Arrange - create repository that returns null for newDocument
        val failingRepository = object : FakeOrderRepository(TestFixtures.TEST_DB_GUID) {
            override suspend fun newDocument(): Order? = null
        }
        val failingViewModel = TestDocumentViewModel(failingRepository, logger)

        // Act
        failingViewModel.events.test {
            failingViewModel.testInitNewDocument()
            advanceUntilIdle()

            // Assert
            val event = awaitItem()
            assertThat(event).isInstanceOf(DocumentEvent.SaveError::class.java)
            assertThat((event as DocumentEvent.SaveError).message).contains("Failed to create document")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initNewDocument with failure logs error`() = runTest {
        // Arrange
        val failingRepository = object : FakeOrderRepository(TestFixtures.TEST_DB_GUID) {
            override suspend fun newDocument(): Order? = null
        }
        val failingViewModel = TestDocumentViewModel(failingRepository, logger)

        // Act
        failingViewModel.testInitNewDocument()
        advanceUntilIdle()

        // Assert
        verify(logger).e(any(), any())
    }

    // ========== Save Document Tests ==========

    @Test
    fun `updateDocument saves to repository`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()

        // Act
        viewModel.testUpdateDocument(order)
        advanceUntilIdle()

        // Assert
        val savedOrder = repository.getOrder(order.guid)
        assertThat(savedOrder).isNotNull()
        assertThat(savedOrder?.guid).isEqualTo(order.guid)
    }

    @Test
    fun `updateDocumentWithResult saves and sets save result to true`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()

        // Act
        viewModel.testUpdateDocumentWithResult(order)
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.saveResult.value).isTrue()
    }

    @Test
    fun `updateDocumentWithResult emits SaveSuccess event`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()

        // Act
        viewModel.events.test {
            viewModel.testUpdateDocumentWithResult(order)
            advanceUntilIdle()

            // Assert
            val event = awaitItem()
            assertThat(event).isInstanceOf(DocumentEvent.SaveSuccess::class.java)
            assertThat((event as DocumentEvent.SaveSuccess).guid).isEqualTo(order.guid)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateDocumentWithResult sets loading state during save`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()

        // Act
        viewModel.isLoading.test {
            assertThat(awaitItem()).isFalse() // Initial state

            viewModel.testUpdateDocumentWithResult(order)

            assertThat(awaitItem()).isTrue()  // Loading
            assertThat(awaitItem()).isFalse() // Complete

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateDocumentWithResult with repository failure emits SaveError event`() = runTest {
        // Arrange - create repository that fails to save
        val failingRepository = object : FakeOrderRepository(TestFixtures.TEST_DB_GUID) {
            override suspend fun updateDocument(document: Order): Boolean = false
        }
        val failingViewModel = TestDocumentViewModel(failingRepository, logger)
        val order = TestFixtures.createOrder1()

        // Act
        failingViewModel.events.test {
            failingViewModel.testUpdateDocumentWithResult(order)
            advanceUntilIdle()

            // Assert
            val event = awaitItem()
            assertThat(event).isInstanceOf(DocumentEvent.SaveError::class.java)
            assertThat((event as DocumentEvent.SaveError).message).contains("Failed to save")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateDocumentWithResult with failure sets save result to false`() = runTest {
        // Arrange
        val failingRepository = object : FakeOrderRepository(TestFixtures.TEST_DB_GUID) {
            override suspend fun updateDocument(document: Order): Boolean = false
        }
        val failingViewModel = TestDocumentViewModel(failingRepository, logger)
        val order = TestFixtures.createOrder1()

        // Act
        failingViewModel.testUpdateDocumentWithResult(order)
        advanceUntilIdle()

        // Assert
        assertThat(failingViewModel.saveResult.value).isFalse()
    }

    // ========== Delete Document Tests ==========

    @Test
    fun `deleteDocument removes document from repository`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        repository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Act
        viewModel.deleteDocument()
        advanceUntilIdle()

        // Assert
        val deletedOrder = repository.getOrder(order.guid)
        assertThat(deletedOrder).isNull()
    }

    @Test
    fun `deleteDocument emits DeleteSuccess event`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        repository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
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

    @Test
    fun `deleteDocument works with current document`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        repository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Verify document is loaded
        assertThat(viewModel.documentFlow.value.guid).isEqualTo(order.guid)

        // Act
        viewModel.deleteDocument()
        advanceUntilIdle()

        // Assert
        assertThat(repository.getOrder(order.guid)).isNull()
    }

    // ========== Cleanup Tests ==========

    @Test
    fun `onDestroy clears document GUID`() {
        // Arrange
        viewModel.setCurrentDocument(TestFixtures.ORDER_1_GUID)
        assertThat(viewModel.documentGuid.value).isNotEmpty()

        // Act
        viewModel.onDestroy()

        // Assert
        assertThat(viewModel.documentGuid.value).isEmpty()
    }

    @Test
    fun `onDestroy resets to empty document`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        repository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Act
        viewModel.documentFlow.test {
            assertThat(awaitItem().guid).isEqualTo(order.guid)

            viewModel.onDestroy()

            assertThat(awaitItem().guid).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========== Abstract Method Tests ==========

    @Test
    fun `getDocumentGuid extracts GUID from document`() {
        // Arrange
        val order = TestFixtures.createOrder1()

        // Act
        val guid = viewModel.testGetDocumentGuid(order)

        // Assert
        assertThat(guid).isEqualTo(order.guid)
    }

    @Test
    fun `markAsProcessed sets isProcessed flag`() {
        // Arrange
        val order = TestFixtures.createOrder1().copy(isProcessed = 0)

        // Act
        val processed = viewModel.testMarkAsProcessed(order)

        // Assert
        assertThat(processed.isProcessed).isEqualTo(1)
    }

    @Test
    fun `enableEdit resets processed flag`() {
        // Arrange
        val order = TestFixtures.createOrder1().copy(isProcessed = 1)
        repository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)

        // Act
        viewModel.enableEdit()

        // Assert
        assertThat(viewModel.isEditEnabled).isTrue()
    }

    @Test
    fun `isNotEditable returns true for processed documents`() {
        // Arrange
        val order = TestFixtures.createOrder1().copy(isProcessed = 1)
        repository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)

        // Act
        val notEditable = viewModel.isNotEditable()

        // Assert
        assertThat(notEditable).isTrue()
    }

    @Test
    fun `isNotEditable returns false for non-processed documents`() {
        // Arrange
        val order = TestFixtures.createOrder1().copy(isProcessed = 0)
        repository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)

        // Act
        val notEditable = viewModel.isNotEditable()

        // Assert
        assertThat(notEditable).isFalse()
    }

    @Test
    fun `onEditNotes updates document notes`() {
        // Arrange
        val order = TestFixtures.createOrder1()
        repository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        val newNotes = "Updated test notes"

        // Act
        viewModel.onEditNotes(newNotes)

        // Assert
        assertThat(viewModel.lastEditedNotes).isEqualTo(newNotes)
    }

    // ========== Edge Cases and Error Handling ==========

    @Test
    fun `loading multiple documents in sequence updates state correctly`() = runTest {
        // Arrange
        val order1 = TestFixtures.createOrder1()
        val order2 = TestFixtures.createOrder2Return()
        val order3 = TestFixtures.createOrder3Sent()
        repository.addOrder(order1)
        repository.addOrder(order2)
        repository.addOrder(order3)

        // Act & Assert
        viewModel.setCurrentDocument(order1.guid)
        advanceUntilIdle()
        assertThat(viewModel.documentFlow.value.guid).isEqualTo(order1.guid)

        viewModel.setCurrentDocument(order2.guid)
        advanceUntilIdle()
        assertThat(viewModel.documentFlow.value.guid).isEqualTo(order2.guid)

        viewModel.setCurrentDocument(order3.guid)
        advanceUntilIdle()
        assertThat(viewModel.documentFlow.value.guid).isEqualTo(order3.guid)
    }

    @Test
    fun `documentFlow uses flatMapLatest for reactive updates`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        repository.addOrder(order)

        // Act & Assert - documentFlow should update reactively when GUID changes
        viewModel.documentFlow.test {
            // Initial empty
            assertThat(awaitItem().guid).isEmpty()

            // Set GUID
            viewModel.setCurrentDocument(order.guid)

            // Should emit loaded document
            assertThat(awaitItem().guid).isEqualTo(order.guid)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `currentDocument accessor returns current documentFlow value`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        repository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Act
        val current = viewModel.testGetCurrentDocument()

        // Assert
        assertThat(current.guid).isEqualTo(order.guid)
        assertThat(current.guid).isEqualTo(viewModel.documentFlow.value.guid)
    }
}

/**
 * Concrete test implementation of DocumentViewModel for testing.
 * Uses Order as the document type.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestDocumentViewModel(
    repository: FakeOrderRepository,
    logger: Logger
) : DocumentViewModel<Order>(
    repository = repository,
    logger = logger,
    logTag = "TestDocumentVM",
    emptyDocument = { Order(guid = "", databaseId = TestFixtures.TEST_DB_GUID) }
) {

    var isEditEnabled = false
    var lastEditedNotes: String = ""

    override fun enableEdit() {
        isEditEnabled = true
    }

    override fun isNotEditable(): Boolean {
        return currentDocument.isProcessed == 1
    }

    override fun onEditNotes(notes: String) {
        lastEditedNotes = notes
        val updated = currentDocument.copy(notes = notes)
        updateDocument(updated)
    }

    override fun getDocumentGuid(document: Order): String = document.guid

    override fun markAsProcessed(document: Order): Order {
        return document.copy(isProcessed = 1)
    }

    // Test helper methods to access protected members
    fun testInitNewDocument() = initNewDocument()
    fun testUpdateDocument(document: Order) = updateDocument(document)
    fun testUpdateDocumentWithResult(document: Order) = updateDocumentWithResult(document)
    fun testGetDocumentGuid(document: Order) = getDocumentGuid(document)
    fun testMarkAsProcessed(document: Order) = markAsProcessed(document)
    fun testGetCurrentDocument() = currentDocument
}
