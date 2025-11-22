package ua.com.programmer.agentventa.catalogs.client

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ua.com.programmer.agentventa.dao.entity.ClientImage
import ua.com.programmer.agentventa.dao.entity.Debt
import ua.com.programmer.agentventa.dao.entity.LClient
import ua.com.programmer.agentventa.fixtures.TestFixtures
import ua.com.programmer.agentventa.repository.ClientRepository
import ua.com.programmer.agentventa.repository.FilesRepository
import ua.com.programmer.agentventa.shared.SharedParameters
import ua.com.programmer.agentventa.util.MainDispatcherRule

/**
 * Test suite for ClientViewModel
 *
 * Covers:
 * - Client details loading and display
 * - Debt list loading
 * - Client images loading
 * - Parameters and company GUID handling
 * - Set default image functionality
 * - Event emission (ImageSetAsDefault, Error)
 * - Reactive state updates
 * - Edge cases and error handling
 */
class ClientViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var clientRepository: ClientRepository
    private lateinit var filesRepository: FilesRepository
    private lateinit var viewModel: ClientViewModel

    // Mock flows
    private lateinit var mockClientFlow: MutableStateFlow<LClient>
    private lateinit var mockDebtsFlow: MutableStateFlow<List<Debt>>
    private lateinit var mockImagesFlow: MutableStateFlow<List<ClientImage>>

    @Before
    fun setup() {
        // Initialize mock flows
        // Default empty client for non-nullable flow
        val defaultClient = LClient(
            guid = "",
            description = "",
            address = "",
            phone = "",
            debt = 0.0,
            isGroup = false,
            groupGuid = "",
            code = ""
        )
        mockClientFlow = MutableStateFlow(defaultClient)
        mockDebtsFlow = MutableStateFlow(emptyList())
        mockImagesFlow = MutableStateFlow(emptyList())

        // Mock repositories
        clientRepository = mock {
            on { getClient(any(), any()) } doReturn mockClientFlow
            on { getDebts(any(), any()) } doReturn mockDebtsFlow
        }

        filesRepository = mock {
            on { getClientImages(any()) } doReturn mockImagesFlow
        }

        viewModel = ClientViewModel(
            clientRepository = clientRepository,
            filesRepository = filesRepository
        )
    }

    // ========================================
    // Helper Methods
    // ========================================

    private fun createTestClient(guid: String = "client-1") = LClient(
        guid = guid,
        description = "Test Client $guid",
        address = "123 Main St",
        phone = "555-1234",
        debt = 1000.0,
        isGroup = false,
        groupGuid = "",
        code = "C001"
    )

    private fun createTestDebts() = listOf(
        Debt(
            databaseId = TestFixtures.TEST_DB_GUID,
            companyGuid = "company-1",
            clientGuid = "client-1",
            docGuid = "debt-1",
            docId = "DOC-001",
            docType = "order",
            sum = 500.0,
            timestamp = System.currentTimeMillis()
        ),
        Debt(
            databaseId = TestFixtures.TEST_DB_GUID,
            companyGuid = "company-1",
            clientGuid = "client-1",
            docGuid = "debt-2",
            docId = "DOC-002",
            docType = "order",
            sum = 500.0,
            timestamp = System.currentTimeMillis()
        )
    )

    private fun createTestImages() = listOf(
        ClientImage(
            guid = "image-1",
            clientGuid = "client-1",
            databaseId = TestFixtures.TEST_DB_GUID,
            url = "http://test.com/image1.jpg",
            description = "Front view",
            timestamp = 1000L,
            isLocal = 0,
            isSent = 1,
            isDefault = 1
        ),
        ClientImage(
            guid = "image-2",
            clientGuid = "client-1",
            databaseId = TestFixtures.TEST_DB_GUID,
            url = "http://test.com/image2.jpg",
            description = "Side view",
            timestamp = 2000L,
            isLocal = 0,
            isSent = 1,
            isDefault = 0
        )
    )

    // ========================================
    // Initial State Tests
    // ========================================

    @Test
    fun `initial client is empty`() = runTest {
        viewModel.clientFlow.test {
            val client = awaitItem()
            assertThat(client?.guid).isEmpty()
            assertThat(client?.description).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial debt list is empty`() = runTest {
        viewModel.debtListFlow.test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial client images list is empty`() = runTest {
        viewModel.clientImagesFlow.test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial parameters have default values`() = runTest {
        viewModel.paramsFlow.test {
            val params = awaitItem()
            assertThat(params.companyGuid).isEmpty()
            assertThat(params.filter).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // Client Loading Tests
    // ========================================

    @Test
    fun `setClientParameters loads client from repository`() = runTest {
        // Arrange
        val client = createTestClient()
        mockClientFlow.value = client

        // Act
        viewModel.setClientParameters(client.guid)
        advanceUntilIdle()

        // Assert
        viewModel.clientFlow.test {
            val loadedClient = awaitItem()
            assertThat(loadedClient).isNotNull()
            assertThat(loadedClient?.guid).isEqualTo(client.guid)
            assertThat(loadedClient?.description).isEqualTo(client.description)
            cancelAndIgnoreRemainingEvents()
        }

        verify(clientRepository).getClient(client.guid, "")
    }

    @Test
    fun `client updates when repository emits new value`() = runTest {
        // Arrange
        val client1 = createTestClient("client-1")
        viewModel.setClientParameters(client1.guid)

        viewModel.clientFlow.test {
            // Initial null
            skipItems(1)

            // First client
            mockClientFlow.value = client1
            var client = awaitItem()
            assertThat(client?.guid).isEqualTo("client-1")

            // Updated client
            val client2 = client1.copy(description = "Updated Description")
            mockClientFlow.value = client2
            client = awaitItem()
            assertThat(client?.description).isEqualTo("Updated Description")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setClientParameters with empty GUID keeps client empty`() = runTest {
        // Act
        viewModel.setClientParameters("")
        advanceUntilIdle()

        // Assert
        viewModel.clientFlow.test {
            val client = awaitItem()
            assertThat(client?.guid).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `client loading respects company GUID from parameters`() = runTest {
        // Arrange
        val params = SharedParameters(companyGuid = "company-123")
        val client = createTestClient()

        viewModel.setParameters(params)
        mockClientFlow.value = client

        // Act
        viewModel.setClientParameters(client.guid)
        advanceUntilIdle()

        // Assert
        verify(clientRepository).getClient(client.guid, "company-123")
    }

    // ========================================
    // Debt List Loading Tests
    // ========================================

    @Test
    fun `debt list loads when client GUID is set`() = runTest {
        // Arrange
        val debts = createTestDebts()
        mockDebtsFlow.value = debts

        // Act
        viewModel.setClientParameters("client-1")
        advanceUntilIdle()

        // Assert
        viewModel.debtListFlow.test {
            val loadedDebts = awaitItem()
            assertThat(loadedDebts).hasSize(2)
            assertThat(loadedDebts[0].docId).isEqualTo("DOC-001")
            assertThat(loadedDebts[1].docId).isEqualTo("DOC-002")
            cancelAndIgnoreRemainingEvents()
        }

        verify(clientRepository).getDebts("client-1", "")
    }

    @Test
    fun `debt list updates when repository emits new values`() = runTest {
        // Arrange
        viewModel.setClientParameters("client-1")

        viewModel.debtListFlow.test {
            // Initial empty
            var debts = awaitItem()
            assertThat(debts).isEmpty()

            // Add debts
            mockDebtsFlow.value = createTestDebts()
            debts = awaitItem()
            assertThat(debts).hasSize(2)

            // Update debts
            val updatedDebts = createTestDebts().subList(0, 1)
            mockDebtsFlow.value = updatedDebts
            debts = awaitItem()
            assertThat(debts).hasSize(1)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `debt list is empty when client GUID is empty`() = runTest {
        // Act
        viewModel.setClientParameters("")
        advanceUntilIdle()

        // Assert
        viewModel.debtListFlow.test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `debt list respects company GUID from parameters`() = runTest {
        // Arrange
        val params = SharedParameters(companyGuid = "company-456")
        viewModel.setParameters(params)

        // Act
        viewModel.setClientParameters("client-1")
        advanceUntilIdle()

        // Assert
        verify(clientRepository).getDebts("client-1", "company-456")
    }

    // ========================================
    // Client Images Loading Tests
    // ========================================

    @Test
    fun `client images load when client GUID is set`() = runTest {
        // Arrange
        val images = createTestImages()
        mockImagesFlow.value = images

        // Act
        viewModel.setClientParameters("client-1")
        advanceUntilIdle()

        // Assert
        viewModel.clientImagesFlow.test {
            val loadedImages = awaitItem()
            assertThat(loadedImages).hasSize(2)
            assertThat(loadedImages[0].description).isEqualTo("Front view")
            assertThat(loadedImages[1].description).isEqualTo("Side view")
            cancelAndIgnoreRemainingEvents()
        }

        verify(filesRepository).getClientImages("client-1")
    }

    @Test
    fun `client images update when repository emits new values`() = runTest {
        // Arrange
        viewModel.setClientParameters("client-1")

        viewModel.clientImagesFlow.test {
            // Initial empty
            var images = awaitItem()
            assertThat(images).isEmpty()

            // Add images
            mockImagesFlow.value = createTestImages()
            images = awaitItem()
            assertThat(images).hasSize(2)

            // Remove one image
            mockImagesFlow.value = createTestImages().subList(0, 1)
            images = awaitItem()
            assertThat(images).hasSize(1)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `client images are empty when client GUID is empty`() = runTest {
        // Act
        viewModel.setClientParameters("")
        advanceUntilIdle()

        // Assert
        viewModel.clientImagesFlow.test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `default image is identified correctly`() = runTest {
        // Arrange
        val images = createTestImages()
        mockImagesFlow.value = images

        viewModel.setClientParameters("client-1")
        advanceUntilIdle()

        // Assert
        viewModel.clientImagesFlow.test {
            val loadedImages = awaitItem()
            val defaultImage = loadedImages.find { it.isDefault == 1 }
            assertThat(defaultImage).isNotNull()
            assertThat(defaultImage?.guid).isEqualTo("image-1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // Parameters Tests
    // ========================================

    @Test
    fun `setParameters updates parameters flow`() = runTest {
        viewModel.paramsFlow.test {
            // Initial
            var params = awaitItem()
            assertThat(params.companyGuid).isEmpty()

            // Update
            val newParams = SharedParameters(
                companyGuid = "company-123",
                filter = "test filter"
            )
            viewModel.setParameters(newParams)

            params = awaitItem()
            assertThat(params.companyGuid).isEqualTo("company-123")
            assertThat(params.filter).isEqualTo("test filter")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `parameters update triggers client reload`() = runTest {
        // Arrange
        val client = createTestClient()
        viewModel.setClientParameters(client.guid)
        mockClientFlow.value = client
        advanceUntilIdle()

        // Act: Change company
        val newParams = SharedParameters(companyGuid = "company-new")
        viewModel.setParameters(newParams)
        advanceUntilIdle()

        // Assert: Client should be reloaded with new company GUID
        verify(clientRepository).getClient(client.guid, "company-new")
    }

    // ========================================
    // Set Default Image Tests
    // ========================================

    @Test
    fun `setDefaultImage calls repository and emits success event`() = runTest {
        // Arrange
        val image = createTestImages()[0]

        // Act
        viewModel.setDefaultImage(image)
        advanceUntilIdle()

        // Assert
        verify(filesRepository).setAsDefault(image)

        viewModel.events.test {
            val event = awaitItem()
            assertThat(event).isInstanceOf(ClientEvent.ImageSetAsDefault::class.java)
            assertThat((event as ClientEvent.ImageSetAsDefault).success).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setDefaultImage emits error event on failure`() = runTest {
        // Arrange
        val image = createTestImages()[0]
        whenever(filesRepository.setAsDefault(any())).thenThrow(
            RuntimeException("Database error")
        )

        // Act
        viewModel.setDefaultImage(image)
        advanceUntilIdle()

        // Assert
        viewModel.events.test {
            val event = awaitItem()
            assertThat(event).isInstanceOf(ClientEvent.Error::class.java)
            assertThat((event as ClientEvent.Error).message).contains("Database error")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setDefaultImage with null exception message uses default error`() = runTest {
        // Arrange
        val image = createTestImages()[0]
        whenever(filesRepository.setAsDefault(any())).thenThrow(
            RuntimeException(null as String?)
        )

        // Act
        viewModel.setDefaultImage(image)
        advanceUntilIdle()

        // Assert
        viewModel.events.test {
            val event = awaitItem()
            assertThat(event).isInstanceOf(ClientEvent.Error::class.java)
            assertThat((event as ClientEvent.Error).message).isEqualTo("Failed to set default image")
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // Combined State Tests
    // ========================================

    @Test
    fun `all flows update simultaneously when client changes`() = runTest {
        // Arrange
        val client = createTestClient()
        val debts = createTestDebts()
        val images = createTestImages()

        // Act
        viewModel.setClientParameters(client.guid)
        mockClientFlow.value = client
        mockDebtsFlow.value = debts
        mockImagesFlow.value = images
        advanceUntilIdle()

        // Assert: All flows have data
        viewModel.clientFlow.test {
            assertThat(awaitItem()).isNotNull()
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.debtListFlow.test {
            assertThat(awaitItem()).hasSize(2)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.clientImagesFlow.test {
            assertThat(awaitItem()).hasSize(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching clients updates all related data`() = runTest {
        // Arrange: First client
        val client1 = createTestClient("client-1")
        viewModel.setClientParameters(client1.guid)
        mockClientFlow.value = client1
        mockDebtsFlow.value = createTestDebts()
        advanceUntilIdle()

        // Act: Switch to second client
        val client2 = createTestClient("client-2")
        viewModel.setClientParameters(client2.guid)
        mockClientFlow.value = client2
        mockDebtsFlow.value = emptyList() // Different debts
        advanceUntilIdle()

        // Assert
        viewModel.clientFlow.test {
            assertThat(awaitItem()?.guid).isEqualTo("client-2")
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.debtListFlow.test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // Edge Cases and Error Handling Tests
    // ========================================

    @Test
    fun `client with no debt displays empty list`() = runTest {
        // Arrange
        val client = createTestClient()
        mockClientFlow.value = client
        mockDebtsFlow.value = emptyList()

        // Act
        viewModel.setClientParameters(client.guid)
        advanceUntilIdle()

        // Assert
        viewModel.debtListFlow.test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `client with no images displays empty list`() = runTest {
        // Arrange
        val client = createTestClient()
        mockClientFlow.value = client
        mockImagesFlow.value = emptyList()

        // Act
        viewModel.setClientParameters(client.guid)
        advanceUntilIdle()

        // Assert
        viewModel.clientImagesFlow.test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `large debt list is handled correctly`() = runTest {
        // Arrange: 100 debts
        val largeDebtList = (1..100).map { index ->
            Debt(
                databaseId = TestFixtures.TEST_DB_GUID,
                companyGuid = "company-1",
                clientGuid = "client-1",
                docGuid = "debt-$index",
                docId = "DOC-$index",
                docType = "order",
                sum = index.toDouble() * 10,
                timestamp = System.currentTimeMillis()
            )
        }

        mockDebtsFlow.value = largeDebtList
        viewModel.setClientParameters("client-1")
        advanceUntilIdle()

        // Assert
        viewModel.debtListFlow.test {
            val debts = awaitItem()
            assertThat(debts).hasSize(100)
            assertThat(debts.first().docId).isEqualTo("DOC-1")
            assertThat(debts.last().docId).isEqualTo("DOC-100")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `multiple parameter updates emit correctly`() = runTest {
        viewModel.paramsFlow.test {
            skipItems(1) // Skip initial

            viewModel.setParameters(SharedParameters(companyGuid = "c1"))
            viewModel.setParameters(SharedParameters(companyGuid = "c2"))
            viewModel.setParameters(SharedParameters(companyGuid = "c3"))

            assertThat(awaitItem().companyGuid).isEqualTo("c1")
            assertThat(awaitItem().companyGuid).isEqualTo("c2")
            assertThat(awaitItem().companyGuid).isEqualTo("c3")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `client with all fields populated displays correctly`() = runTest {
        // Arrange
        val fullClient = LClient(
            guid = "full-client",
            description = "Full Client Inc.",
            address = "123 Business Park, Suite 456",
            phone = "+1-555-123-4567",
            debt = 12345.67,
            isGroup = false,
            groupGuid = "parent-group",
            code = "FC001",
            notes = "Important client",
            discount = 10.0,
            bonus = 5.0,
            priceType = "Retail",
            isBanned = false,
            banMessage = "",
            groupName = "Parent Group",
            isActive = true,
            latitude = 50.4501,
            longitude = 30.5234
        )

        mockClientFlow.value = fullClient
        viewModel.setClientParameters(fullClient.guid)
        advanceUntilIdle()

        // Assert
        viewModel.clientFlow.test {
            val client = awaitItem()
            assertThat(client?.guid).isEqualTo("full-client")
            assertThat(client?.description).isEqualTo("Full Client Inc.")
            assertThat(client?.address).isEqualTo("123 Business Park, Suite 456")
            assertThat(client?.phone).isEqualTo("+1-555-123-4567")
            assertThat(client?.debt).isEqualTo(12345.67)
            assertThat(client?.notes).isEqualTo("Important client")
            assertThat(client?.discount).isEqualTo(10.0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setting same client GUID multiple times works correctly`() = runTest {
        // Arrange
        val client = createTestClient()
        mockClientFlow.value = client

        // Act: Set same GUID multiple times
        viewModel.setClientParameters(client.guid)
        viewModel.setClientParameters(client.guid)
        viewModel.setClientParameters(client.guid)
        advanceUntilIdle()

        // Assert: Client still loaded correctly
        viewModel.clientFlow.test {
            assertThat(awaitItem()?.guid).isEqualTo(client.guid)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
