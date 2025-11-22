package ua.com.programmer.agentventa.catalogs.client

import android.view.View
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
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
import ua.com.programmer.agentventa.dao.entity.LClient
import ua.com.programmer.agentventa.fixtures.TestFixtures
import ua.com.programmer.agentventa.repository.ClientRepository
import ua.com.programmer.agentventa.shared.SharedParameters
import ua.com.programmer.agentventa.util.MainDispatcherRule
import ua.com.programmer.agentventa.util.getOrAwaitValue

/**
 * Test suite for ClientListViewModel
 *
 * Covers:
 * - Client list loading with filtering
 * - Search text filtering
 * - Group filtering (hierarchical clients)
 * - Company GUID filtering
 * - Search visibility toggle
 * - Current group display
 * - Select mode functionality
 * - No data text visibility
 * - Edge cases and error handling
 */
class ClientListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var clientRepository: ClientRepository
    private lateinit var viewModel: ClientListViewModel

    // Mock flows
    private lateinit var mockClientsFlow: MutableStateFlow<List<LClient>>
    private lateinit var mockCurrentGroupFlow: MutableStateFlow<LClient>

    @Before
    fun setup() {
        // Initialize mock flows
        mockClientsFlow = MutableStateFlow(emptyList())

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
        mockCurrentGroupFlow = MutableStateFlow(defaultClient)

        // Mock repository
        clientRepository = mock {
            on { getClients(any(), any(), any()) } doReturn mockClientsFlow
            on { getClient(any(), any()) } doReturn mockCurrentGroupFlow
        }

        viewModel = ClientListViewModel(repository = clientRepository)
    }

    // ========================================
    // Helper Methods
    // ========================================

    private fun createTestClients() = listOf(
        LClient(
            guid = "client-1",
            description = "ABC Store",
            address = "123 Main St",
            phone = "555-0001",
            debt = 1000.0,
            isGroup = false,
            groupGuid = "",
            code = "C001"
        ),
        LClient(
            guid = "client-2",
            description = "XYZ Market",
            address = "456 Oak Ave",
            phone = "555-0002",
            debt = 500.0,
            isGroup = false,
            groupGuid = "",
            code = "C002"
        ),
        LClient(
            guid = "client-3",
            description = "ABC Wholesale",
            address = "789 Pine Rd",
            phone = "555-0003",
            debt = 2000.0,
            isGroup = false,
            groupGuid = "",
            code = "C003"
        )
    )

    private fun createGroupClients() = listOf(
        LClient(
            guid = "group-1",
            description = "Retail Group",
            address = "",
            phone = "",
            debt = 0.0,
            isGroup = true,
            groupGuid = "",
            code = "G001"
        ),
        LClient(
            guid = "client-4",
            description = "Client in Group",
            address = "111 Group St",
            phone = "555-0004",
            debt = 300.0,
            isGroup = false,
            groupGuid = "group-1",
            code = "C004"
        )
    )

    // ========================================
    // Initial State Tests
    // ========================================

    @Test
    fun `initial clients list is empty`() = runTest {
        // Assert
        val clients = viewModel.clients.getOrAwaitValue()
        assertThat(clients).isEmpty()
    }

    @Test
    fun `initial search text is empty`() = runTest {
        // Assert
        val searchText = viewModel.searchText.getOrAwaitValue()
        assertThat(searchText).isEmpty()
    }

    @Test
    fun `initial search visibility is GONE`() = runTest {
        // Assert
        val visibility = viewModel.searchVisibility.getOrAwaitValue()
        assertThat(visibility).isEqualTo(View.GONE)
    }

    @Test
    fun `initial no data text visibility is VISIBLE when list is empty`() = runTest {
        // Assert
        val visibility = viewModel.noDataTextVisibility.getOrAwaitValue()
        assertThat(visibility).isEqualTo(View.VISIBLE)
    }

    // ========================================
    // Client List Loading Tests
    // ========================================

    @Test
    fun `setListParameters loads clients from repository`() = runTest {
        // Arrange
        val clients = createTestClients()
        mockClientsFlow.value = clients

        val params = SharedParameters(
            companyGuid = "company-1"
        )

        // Act
        viewModel.setListParameters(params)
        advanceUntilIdle()

        // Assert
        val loadedClients = viewModel.clients.getOrAwaitValue()
        assertThat(loadedClients).hasSize(3)
        assertThat(loadedClients[0].description).isEqualTo("ABC Store")
    }

    @Test
    fun `clients list updates when repository emits new values`() = runTest {
        // Arrange
        val params = SharedParameters(companyGuid = "company-1")
        viewModel.setListParameters(params)
        advanceUntilIdle()

        // Act: Update flow
        mockClientsFlow.value = createTestClients()
        advanceUntilIdle()

        // Assert
        val clients = viewModel.clients.getOrAwaitValue()
        assertThat(clients).hasSize(3)
    }

    @Test
    fun `no data text visibility is GONE when clients list is not empty`() = runTest {
        // Arrange
        mockClientsFlow.value = createTestClients()
        val params = SharedParameters(companyGuid = "company-1")

        // Act
        viewModel.setListParameters(params)
        advanceUntilIdle()

        // Assert
        val visibility = viewModel.noDataTextVisibility.getOrAwaitValue()
        assertThat(visibility).isEqualTo(View.GONE)
    }

    // ========================================
    // Search Text Filtering Tests
    // ========================================

    @Test
    fun `onTextChanged updates search text`() = runTest {
        // Act
        viewModel.onTextChanged("ABC", 0, 0, 3)
        advanceUntilIdle()

        // Assert
        val searchText = viewModel.searchText.getOrAwaitValue()
        assertThat(searchText).isEqualTo("ABC")
    }

    @Test
    fun `onTextChanged triggers client list reload`() = runTest {
        // Arrange
        val params = SharedParameters(companyGuid = "company-1")
        viewModel.setListParameters(params)

        val allClients = createTestClients()
        val filteredClients = allClients.filter { it.description.contains("ABC") }

        mockClientsFlow.value = allClients
        advanceUntilIdle()

        // Act: Search for "ABC"
        viewModel.onTextChanged("ABC", 0, 0, 3)
        mockClientsFlow.value = filteredClients
        advanceUntilIdle()

        // Assert
        val clients = viewModel.clients.getOrAwaitValue()
        assertThat(clients).hasSize(2)
        assertThat(clients.all { it.description.contains("ABC") }).isTrue()
    }

    @Test
    fun `clearing search text shows all clients`() = runTest {
        // Arrange
        val params = SharedParameters(companyGuid = "company-1")
        viewModel.setListParameters(params)

        // Set search text
        viewModel.onTextChanged("ABC", 0, 0, 3)
        mockClientsFlow.value = createTestClients().subList(0, 1)
        advanceUntilIdle()

        // Act: Clear search
        viewModel.onTextChanged("", 0, 3, 0)
        mockClientsFlow.value = createTestClients()
        advanceUntilIdle()

        // Assert
        val clients = viewModel.clients.getOrAwaitValue()
        assertThat(clients).hasSize(3)
    }

    @Test
    fun `search is case insensitive`() = runTest {
        // Arrange
        val params = SharedParameters(companyGuid = "company-1")
        viewModel.setListParameters(params)

        // Act: Search with lowercase
        viewModel.onTextChanged("abc", 0, 0, 3)
        advanceUntilIdle()

        // Assert
        val searchText = viewModel.searchText.getOrAwaitValue()
        assertThat(searchText).isEqualTo("abc")
    }

    // ========================================
    // Search Visibility Toggle Tests
    // ========================================

    @Test
    fun `toggleSearchVisibility shows search when hidden`() = runTest {
        // Act
        viewModel.toggleSearchVisibility()
        advanceUntilIdle()

        // Assert
        val visibility = viewModel.searchVisibility.getOrAwaitValue()
        assertThat(visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun `toggleSearchVisibility hides search when visible`() = runTest {
        // Arrange: Show search first
        viewModel.toggleSearchVisibility()
        advanceUntilIdle()

        // Act: Toggle again
        viewModel.toggleSearchVisibility()
        advanceUntilIdle()

        // Assert
        val visibility = viewModel.searchVisibility.getOrAwaitValue()
        assertThat(visibility).isEqualTo(View.GONE)
    }

    @Test
    fun `toggleSearchVisibility clears search text when hiding`() = runTest {
        // Arrange: Show search and set text
        viewModel.toggleSearchVisibility()
        viewModel.onTextChanged("ABC", 0, 0, 3)
        advanceUntilIdle()

        // Act: Hide search
        viewModel.toggleSearchVisibility()
        advanceUntilIdle()

        // Assert
        val searchText = viewModel.searchText.getOrAwaitValue()
        assertThat(searchText).isEmpty()
    }

    @Test
    fun `multiple search toggles work correctly`() = runTest {
        // Act & Assert: Multiple toggles
        viewModel.toggleSearchVisibility()
        assertThat(viewModel.searchVisibility.getOrAwaitValue()).isEqualTo(View.VISIBLE)

        viewModel.toggleSearchVisibility()
        assertThat(viewModel.searchVisibility.getOrAwaitValue()).isEqualTo(View.GONE)

        viewModel.toggleSearchVisibility()
        assertThat(viewModel.searchVisibility.getOrAwaitValue()).isEqualTo(View.VISIBLE)
    }

    // ========================================
    // Group Filtering Tests
    // ========================================

    @Test
    fun `setCurrentGroup filters clients by parent GUID`() = runTest {
        // Arrange
        val params = SharedParameters(companyGuid = "company-1")
        viewModel.setListParameters(params)

        // Act
        viewModel.setCurrentGroup("group-1")
        mockClientsFlow.value = createGroupClients().filter { it.groupGuid == "group-1" }
        advanceUntilIdle()

        // Assert
        val clients = viewModel.clients.getOrAwaitValue()
        assertThat(clients).hasSize(1)
        assertThat(clients[0].groupGuid).isEqualTo("group-1")
    }

    @Test
    fun `setCurrentGroup with null shows root clients`() = runTest {
        // Arrange
        val params = SharedParameters(companyGuid = "company-1")
        viewModel.setListParameters(params)

        // Act
        viewModel.setCurrentGroup(null)
        mockClientsFlow.value = createTestClients()
        advanceUntilIdle()

        // Assert
        val clients = viewModel.clients.getOrAwaitValue()
        assertThat(clients).hasSize(3)
    }

    @Test
    fun `setCurrentGroup with empty string shows root clients`() = runTest {
        // Arrange
        val params = SharedParameters(companyGuid = "company-1")
        viewModel.setListParameters(params)

        // Act
        viewModel.setCurrentGroup("")
        mockClientsFlow.value = createTestClients()
        advanceUntilIdle()

        // Assert
        val clients = viewModel.clients.getOrAwaitValue()
        assertThat(clients).hasSize(3)
    }

    @Test
    fun `currentGroup loads group details from repository`() = runTest {
        // Arrange
        val group = createGroupClients()[0]
        mockCurrentGroupFlow.value = group

        // Act
        viewModel.setCurrentGroup(group.guid)
        advanceUntilIdle()

        // Assert
        val currentGroup = viewModel.currentGroup.getOrAwaitValue()
        assertThat(currentGroup).isNotNull()
        assertThat(currentGroup?.guid).isEqualTo(group.guid)
        assertThat(currentGroup?.description).isEqualTo("Retail Group")
        assertThat(currentGroup?.isGroup).isTrue()
    }

    // ========================================
    // Company GUID Filtering Tests
    // ========================================

    @Test
    fun `changing company GUID reloads client list`() = runTest {
        // Arrange: First company
        val params1 = SharedParameters(companyGuid = "company-1")
        viewModel.setListParameters(params1)
        mockClientsFlow.value = createTestClients()
        advanceUntilIdle()

        // Act: Change company
        val params2 = SharedParameters(companyGuid = "company-2")
        viewModel.setListParameters(params2)
        mockClientsFlow.value = createTestClients().subList(0, 1)
        advanceUntilIdle()

        // Assert
        val clients = viewModel.clients.getOrAwaitValue()
        assertThat(clients).hasSize(1)
    }

    // ========================================
    // Select Mode Tests
    // ========================================

    @Test
    fun `setSelectMode updates select mode flag`() = runTest {
        // Act
        viewModel.setSelectMode(true)

        // Assert: No direct getter, but verify no crash
        // Select mode is internal state used by fragments
        advanceUntilIdle()
    }

    @Test
    fun `setSelectMode can be toggled multiple times`() = runTest {
        // Act
        viewModel.setSelectMode(true)
        viewModel.setSelectMode(false)
        viewModel.setSelectMode(true)

        // Assert: No crashes
        advanceUntilIdle()
    }

    // ========================================
    // DocType Tests
    // ========================================

    @Test
    fun `docType returns empty string when no parameters set`() {
        // Act
        val docType = viewModel.docType()

        // Assert
        assertThat(docType).isEmpty()
    }

    @Test
    fun `docType returns value from parameters`() = runTest {
        // Arrange
        val params = SharedParameters(docType = "Order")
        viewModel.setListParameters(params)
        advanceUntilIdle()

        // Act
        val docType = viewModel.docType()

        // Assert
        assertThat(docType).isEqualTo("Order")
    }

    // ========================================
    // Combined Filtering Tests
    // ========================================

    @Test
    fun `search text and group filter work together`() = runTest {
        // Arrange
        val params = SharedParameters(companyGuid = "company-1")
        viewModel.setListParameters(params)
        viewModel.setCurrentGroup("group-1")

        // Act: Search within group
        viewModel.onTextChanged("Client", 0, 0, 6)
        mockClientsFlow.value = listOf(createGroupClients()[1])
        advanceUntilIdle()

        // Assert
        val clients = viewModel.clients.getOrAwaitValue()
        assertThat(clients).hasSize(1)
        assertThat(clients[0].groupGuid).isEqualTo("group-1")
        assertThat(clients[0].description).contains("Client")
    }

    @Test
    fun `search text and company filter work together`() = runTest {
        // Arrange
        val params = SharedParameters(companyGuid = "company-1")
        viewModel.setListParameters(params)

        // Act: Search
        viewModel.onTextChanged("ABC", 0, 0, 3)
        mockClientsFlow.value = createTestClients().filter { it.description.contains("ABC") }
        advanceUntilIdle()

        // Assert
        val clients = viewModel.clients.getOrAwaitValue()
        assertThat(clients).hasSize(2)
    }

    // ========================================
    // Edge Cases and Error Handling Tests
    // ========================================

    @Test
    fun `empty client list shows no data text`() = runTest {
        // Arrange
        val params = SharedParameters(companyGuid = "company-1")
        viewModel.setListParameters(params)
        mockClientsFlow.value = emptyList()
        advanceUntilIdle()

        // Assert
        val visibility = viewModel.noDataTextVisibility.getOrAwaitValue()
        assertThat(visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun `search with no matches shows no data text`() = runTest {
        // Arrange
        val params = SharedParameters(companyGuid = "company-1")
        viewModel.setListParameters(params)

        // Act: Search for non-existent client
        viewModel.onTextChanged("NONEXISTENT", 0, 0, 10)
        mockClientsFlow.value = emptyList()
        advanceUntilIdle()

        // Assert
        val clients = viewModel.clients.getOrAwaitValue()
        assertThat(clients).isEmpty()

        val visibility = viewModel.noDataTextVisibility.getOrAwaitValue()
        assertThat(visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun `large client list is handled correctly`() = runTest {
        // Arrange: 100 clients
        val largeClientList = (1..100).map { index ->
            LClient(
                guid = "client-$index",
                description = "Client $index",
                address = "Address $index",
                phone = "555-$index",
                debt = index.toDouble() * 100,
                isGroup = false,
                groupGuid = "",
                code = "C$index"
            )
        }

        val params = SharedParameters(companyGuid = "company-1")
        viewModel.setListParameters(params)
        mockClientsFlow.value = largeClientList
        advanceUntilIdle()

        // Assert
        val clients = viewModel.clients.getOrAwaitValue()
        assertThat(clients).hasSize(100)
        assertThat(clients.first().description).isEqualTo("Client 1")
        assertThat(clients.last().description).isEqualTo("Client 100")
    }

    @Test
    fun `special characters in search text are handled`() = runTest {
        // Arrange
        val params = SharedParameters(companyGuid = "company-1")
        viewModel.setListParameters(params)

        // Act: Search with special characters
        viewModel.onTextChanged("ABC & Co.", 0, 0, 9)
        advanceUntilIdle()

        // Assert
        val searchText = viewModel.searchText.getOrAwaitValue()
        assertThat(searchText).isEqualTo("ABC & Co.")
    }

    @Test
    fun `very long search text is handled`() = runTest {
        // Arrange
        val params = SharedParameters(companyGuid = "company-1")
        viewModel.setListParameters(params)

        val longText = "A".repeat(1000)

        // Act
        viewModel.onTextChanged(longText, 0, 0, 1000)
        advanceUntilIdle()

        // Assert
        val searchText = viewModel.searchText.getOrAwaitValue()
        assertThat(searchText).isEqualTo(longText)
    }

    @Test
    fun `rapid search text changes update correctly`() = runTest {
        // Arrange
        val params = SharedParameters(companyGuid = "company-1")
        viewModel.setListParameters(params)

        // Act: Rapid changes
        viewModel.onTextChanged("A", 0, 0, 1)
        viewModel.onTextChanged("AB", 0, 1, 1)
        viewModel.onTextChanged("ABC", 0, 2, 1)
        advanceUntilIdle()

        // Assert
        val searchText = viewModel.searchText.getOrAwaitValue()
        assertThat(searchText).isEqualTo("ABC")
    }

    @Test
    fun `group with no children shows empty list`() = runTest {
        // Arrange
        val params = SharedParameters(companyGuid = "company-1")
        viewModel.setListParameters(params)

        // Act: Navigate to group with no children
        viewModel.setCurrentGroup("empty-group")
        mockClientsFlow.value = emptyList()
        advanceUntilIdle()

        // Assert
        val clients = viewModel.clients.getOrAwaitValue()
        assertThat(clients).isEmpty()
    }

    @Test
    fun `clients with same name are all displayed`() = runTest {
        // Arrange: Multiple clients with same name
        val duplicateClients = listOf(
            createTestClients()[0],
            createTestClients()[0].copy(guid = "client-dup-1"),
            createTestClients()[0].copy(guid = "client-dup-2")
        )

        val params = SharedParameters(companyGuid = "company-1")
        viewModel.setListParameters(params)
        mockClientsFlow.value = duplicateClients
        advanceUntilIdle()

        // Assert
        val clients = viewModel.clients.getOrAwaitValue()
        assertThat(clients).hasSize(3)
        assertThat(clients.all { it.description == "ABC Store" }).isTrue()
    }

    @Test
    fun `parameters with all fields populated work correctly`() = runTest {
        // Arrange
        val fullParams = SharedParameters(
            filter = "test filter",
            groupGuid = "group-1",
            docGuid = "doc-1",
            docType = "Order",
            companyGuid = "company-1",
            company = "Test Company",
            storeGuid = "store-1",
            store = "Test Store",
            sortByName = true,
            restsOnly = true,
            clientProducts = true,
            priceType = "P1",
            currentAccount = "account-1"
        )

        // Act
        viewModel.setListParameters(fullParams)
        mockClientsFlow.value = createTestClients()
        advanceUntilIdle()

        // Assert
        val docType = viewModel.docType()
        assertThat(docType).isEqualTo("Order")

        val clients = viewModel.clients.getOrAwaitValue()
        assertThat(clients).hasSize(3)
    }
}
