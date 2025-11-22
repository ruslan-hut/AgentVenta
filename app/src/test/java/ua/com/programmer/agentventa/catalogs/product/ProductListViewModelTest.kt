package ua.com.programmer.agentventa.catalogs.product

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
import ua.com.programmer.agentventa.dao.entity.LProduct
import ua.com.programmer.agentventa.fixtures.TestFixtures
import ua.com.programmer.agentventa.repository.ProductRepository
import ua.com.programmer.agentventa.shared.SharedParameters
import ua.com.programmer.agentventa.util.MainDispatcherRule
import ua.com.programmer.agentventa.util.getOrAwaitValue

/**
 * Test suite for ProductListViewModel
 *
 * Covers:
 * - Product list loading with filtering
 * - Search text filtering
 * - Group filtering (hierarchical products/categories)
 * - Parameters handling
 * - Search visibility toggle
 * - Current group display
 * - Select mode functionality
 * - No data text visibility
 * - Edge cases and error handling
 */
class ProductListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var productRepository: ProductRepository
    private lateinit var viewModel: ProductListViewModel

    // Mock flows
    private lateinit var mockProductsFlow: MutableStateFlow<List<LProduct>>
    private lateinit var mockCurrentGroupFlow: MutableStateFlow<LProduct>

    @Before
    fun setup() {
        // Initialize mock flows
        mockProductsFlow = MutableStateFlow(emptyList())

        // Default empty product for non-nullable flow
        val defaultProduct = LProduct(
            guid = "",
            description = "",
            code = "",
            vendorCode = "",
            isGroup = false
        )
        mockCurrentGroupFlow = MutableStateFlow(defaultProduct)

        // Mock repository
        productRepository = mock {
            on { getProducts(any()) } doReturn mockProductsFlow
            on { getProduct(any()) } doReturn mockCurrentGroupFlow
        }

        viewModel = ProductListViewModel(productRepository = productRepository)
    }

    // ========================================
    // Helper Methods
    // ========================================

    private fun createTestProducts() = listOf(
        LProduct(
            guid = "product-1",
            description = "Laptop ABC",
            vendorCode = "LAP001",
            price = 1000.0,
            quantity = 10.0,
            rest = 50.0,
            isGroup = false,
            code = "P001"
        ),
        LProduct(
            guid = "product-2",
            description = "Mouse XYZ",
            vendorCode = "MOU001",
            price = 25.0,
            quantity = 100.0,
            rest = 200.0,
            isGroup = false,
            code = "P002"
        ),
        LProduct(
            guid = "product-3",
            description = "Keyboard ABC Pro",
            vendorCode = "KEY001",
            price = 75.0,
            quantity = 50.0,
            rest = 100.0,
            isGroup = false,
            code = "P003"
        )
    )

    private fun createGroupProducts() = listOf(
        LProduct(
            guid = "group-electronics",
            description = "Electronics",
            vendorCode = "",
            price = 0.0,
            quantity = 0.0,
            rest = 0.0,
            isGroup = true,
            code = "GRP001",
            groupName = ""
        ),
        LProduct(
            guid = "product-in-group",
            description = "Laptop in Electronics",
            vendorCode = "LAP002",
            price = 1500.0,
            quantity = 5.0,
            rest = 10.0,
            isGroup = false,
            code = "P004",
            groupName = "Electronics"
        )
    )

    // ========================================
    // Initial State Tests
    // ========================================

    @Test
    fun `initial products list is empty`() = runTest {
        // Assert
        val products = viewModel.products.getOrAwaitValue()
        assertThat(products).isEmpty()
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
    fun `initial no data text visibility is VISIBLE`() = runTest {
        // Assert
        val visibility = viewModel.noDataTextVisibility.getOrAwaitValue()
        assertThat(visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun `initial select mode is false`() {
        // Assert
        assertThat(viewModel.selectMode).isFalse()
    }

    // ========================================
    // Product List Loading Tests
    // ========================================

    @Test
    fun `setListParams loads products from repository`() = runTest {
        // Arrange
        val products = createTestProducts()
        mockProductsFlow.value = products

        val params = SharedParameters(
            priceType = "P1",
            sortByName = true
        )

        // Act
        viewModel.setListParams(params)
        advanceUntilIdle()

        // Assert
        val loadedProducts = viewModel.products.getOrAwaitValue()
        assertThat(loadedProducts).hasSize(3)
        assertThat(loadedProducts[0].description).isEqualTo("Laptop ABC")
    }

    @Test
    fun `products list updates when repository emits new values`() = runTest {
        // Arrange
        val params = SharedParameters()
        viewModel.setListParams(params)
        advanceUntilIdle()

        // Act: Update flow
        mockProductsFlow.value = createTestProducts()
        advanceUntilIdle()

        // Assert
        val products = viewModel.products.getOrAwaitValue()
        assertThat(products).hasSize(3)
    }

    @Test
    fun `no data text visibility is GONE when products list is not empty`() = runTest {
        // Arrange
        mockProductsFlow.value = createTestProducts()
        val params = SharedParameters()

        // Act
        viewModel.setListParams(params)
        advanceUntilIdle()

        // Assert
        val visibility = viewModel.noDataTextVisibility.getOrAwaitValue()
        assertThat(visibility).isEqualTo(View.GONE)
    }

    @Test
    fun `no data text visibility is VISIBLE when products list is empty`() = runTest {
        // Arrange
        mockProductsFlow.value = emptyList()
        val params = SharedParameters()

        // Act
        viewModel.setListParams(params)
        advanceUntilIdle()

        // Assert
        val visibility = viewModel.noDataTextVisibility.getOrAwaitValue()
        assertThat(visibility).isEqualTo(View.VISIBLE)
    }

    // ========================================
    // Search Text Filtering Tests
    // ========================================

    @Test
    fun `onTextChanged updates search text`() = runTest {
        // Act
        viewModel.onTextChanged("Laptop", 0, 0, 6)
        advanceUntilIdle()

        // Assert
        val searchText = viewModel.searchText.getOrAwaitValue()
        assertThat(searchText).isEqualTo("Laptop")
    }

    @Test
    fun `onTextChanged triggers product list reload`() = runTest {
        // Arrange
        val params = SharedParameters()
        viewModel.setListParams(params)

        val allProducts = createTestProducts()
        val filteredProducts = allProducts.filter { it.description.contains("ABC") }

        mockProductsFlow.value = allProducts
        advanceUntilIdle()

        // Act: Search for "ABC"
        viewModel.onTextChanged("ABC", 0, 0, 3)
        mockProductsFlow.value = filteredProducts
        advanceUntilIdle()

        // Assert
        val products = viewModel.products.getOrAwaitValue()
        assertThat(products).hasSize(2)
        assertThat(products.all { it.description.contains("ABC") }).isTrue()
    }

    @Test
    fun `clearing search text shows all products`() = runTest {
        // Arrange
        val params = SharedParameters()
        viewModel.setListParams(params)

        // Set search text
        viewModel.onTextChanged("ABC", 0, 0, 3)
        mockProductsFlow.value = createTestProducts().subList(0, 1)
        advanceUntilIdle()

        // Act: Clear search
        viewModel.onTextChanged("", 0, 3, 0)
        mockProductsFlow.value = createTestProducts()
        advanceUntilIdle()

        // Assert
        val products = viewModel.products.getOrAwaitValue()
        assertThat(products).hasSize(3)
    }

    @Test
    fun `search by product code works`() = runTest {
        // Arrange
        val params = SharedParameters()
        viewModel.setListParams(params)

        // Act: Search by code
        viewModel.onTextChanged("P001", 0, 0, 4)
        advanceUntilIdle()

        // Assert
        val searchText = viewModel.searchText.getOrAwaitValue()
        assertThat(searchText).isEqualTo("P001")
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
        viewModel.onTextChanged("Laptop", 0, 0, 6)
        advanceUntilIdle()

        // Act: Hide search
        viewModel.toggleSearchVisibility()
        advanceUntilIdle()

        // Assert
        val searchText = viewModel.searchText.getOrAwaitValue()
        assertThat(searchText).isEmpty()
    }

    // ========================================
    // Group Filtering Tests
    // ========================================

    @Test
    fun `setCurrentGroup filters products by parent GUID`() = runTest {
        // Arrange
        val params = SharedParameters()
        viewModel.setListParams(params)

        // Act
        viewModel.setCurrentGroup("group-electronics")
        mockProductsFlow.value = createGroupProducts().filter { it.groupName == "Electronics" }
        advanceUntilIdle()

        // Assert
        val products = viewModel.products.getOrAwaitValue()
        assertThat(products).hasSize(1)
        assertThat(products[0].groupName).isEqualTo("Electronics")
    }

    @Test
    fun `setCurrentGroup with null shows root products`() = runTest {
        // Arrange
        val params = SharedParameters()
        viewModel.setListParams(params)

        // Act
        viewModel.setCurrentGroup(null)
        mockProductsFlow.value = createTestProducts()
        advanceUntilIdle()

        // Assert
        val products = viewModel.products.getOrAwaitValue()
        assertThat(products).hasSize(3)
    }

    @Test
    fun `setCurrentGroup with empty string shows root products`() = runTest {
        // Arrange
        val params = SharedParameters()
        viewModel.setListParams(params)

        // Act
        viewModel.setCurrentGroup("")
        mockProductsFlow.value = createTestProducts()
        advanceUntilIdle()

        // Assert
        val products = viewModel.products.getOrAwaitValue()
        assertThat(products).hasSize(3)
    }

    @Test
    fun `currentGroup loads group details from repository`() = runTest {
        // Arrange
        val group = createGroupProducts()[0]
        mockCurrentGroupFlow.value = group

        // Act
        viewModel.setCurrentGroup(group.guid)
        advanceUntilIdle()

        // Assert
        val currentGroup = viewModel.currentGroup.getOrAwaitValue()
        assertThat(currentGroup).isNotNull()
        assertThat(currentGroup?.guid).isEqualTo(group.guid)
        assertThat(currentGroup?.description).isEqualTo("Electronics")
        assertThat(currentGroup?.isGroup).isEqualTo(1)
    }

    // ========================================
    // Select Mode Tests
    // ========================================

    @Test
    fun `setSelectMode updates select mode flag`() {
        // Act
        viewModel.setSelectMode(true)

        // Assert
        assertThat(viewModel.selectMode).isTrue()
    }

    @Test
    fun `setSelectMode can be toggled`() {
        // Act
        viewModel.setSelectMode(true)
        assertThat(viewModel.selectMode).isTrue()

        viewModel.setSelectMode(false)
        assertThat(viewModel.selectMode).isFalse()
    }

    // ========================================
    // Parameters Tests
    // ========================================

    @Test
    fun `parameters with sort by name work correctly`() = runTest {
        // Arrange
        val params = SharedParameters(sortByName = true)
        mockProductsFlow.value = createTestProducts()

        // Act
        viewModel.setListParams(params)
        advanceUntilIdle()

        // Assert
        val products = viewModel.products.getOrAwaitValue()
        assertThat(products).hasSize(3)
    }

    @Test
    fun `parameters with restsOnly filter work correctly`() = runTest {
        // Arrange
        val params = SharedParameters(restsOnly = true)
        mockProductsFlow.value = createTestProducts().filter { it.rest > 0 }

        // Act
        viewModel.setListParams(params)
        advanceUntilIdle()

        // Assert
        val products = viewModel.products.getOrAwaitValue()
        assertThat(products.all { it.rest > 0 }).isTrue()
    }

    @Test
    fun `parameters with clientProducts filter work correctly`() = runTest {
        // Arrange
        val params = SharedParameters(clientProducts = true)
        mockProductsFlow.value = createTestProducts()

        // Act
        viewModel.setListParams(params)
        advanceUntilIdle()

        // Assert
        val products = viewModel.products.getOrAwaitValue()
        assertThat(products).hasSize(3)
    }

    // ========================================
    // Combined Filtering Tests
    // ========================================

    @Test
    fun `search text and group filter work together`() = runTest {
        // Arrange
        val params = SharedParameters()
        viewModel.setListParams(params)
        viewModel.setCurrentGroup("group-electronics")

        // Act: Search within group
        viewModel.onTextChanged("Laptop", 0, 0, 6)
        mockProductsFlow.value = listOf(createGroupProducts()[1])
        advanceUntilIdle()

        // Assert
        val products = viewModel.products.getOrAwaitValue()
        assertThat(products).hasSize(1)
        assertThat(products[0].groupName).isEqualTo("Electronics")
        assertThat(products[0].description).contains("Laptop")
    }

    // ========================================
    // Edge Cases and Error Handling Tests
    // ========================================

    @Test
    fun `empty product list shows no data text`() = runTest {
        // Arrange
        val params = SharedParameters()
        viewModel.setListParams(params)
        mockProductsFlow.value = emptyList()
        advanceUntilIdle()

        // Assert
        val visibility = viewModel.noDataTextVisibility.getOrAwaitValue()
        assertThat(visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun `search with no matches shows no data text`() = runTest {
        // Arrange
        val params = SharedParameters()
        viewModel.setListParams(params)

        // Act: Search for non-existent product
        viewModel.onTextChanged("NONEXISTENT", 0, 0, 10)
        mockProductsFlow.value = emptyList()
        advanceUntilIdle()

        // Assert
        val products = viewModel.products.getOrAwaitValue()
        assertThat(products).isEmpty()

        val visibility = viewModel.noDataTextVisibility.getOrAwaitValue()
        assertThat(visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun `large product list is handled correctly`() = runTest {
        // Arrange: 100 products
        val largeProductList = (1..100).map { index ->
            LProduct(
                guid = "product-$index",
                description = "Product $index",
                vendorCode = "VND$index",
                price = index.toDouble() * 10,
                quantity = index.toDouble(),
                rest = index.toDouble() * 2,
                isGroup = false,
                code = "P$index"
            )
        }

        val params = SharedParameters()
        viewModel.setListParams(params)
        mockProductsFlow.value = largeProductList
        advanceUntilIdle()

        // Assert
        val products = viewModel.products.getOrAwaitValue()
        assertThat(products).hasSize(100)
        assertThat(products.first().description).isEqualTo("Product 1")
        assertThat(products.last().description).isEqualTo("Product 100")
    }

    @Test
    fun `special characters in search text are handled`() = runTest {
        // Arrange
        val params = SharedParameters()
        viewModel.setListParams(params)

        // Act: Search with special characters
        viewModel.onTextChanged("Laptop & Mouse", 0, 0, 14)
        advanceUntilIdle()

        // Assert
        val searchText = viewModel.searchText.getOrAwaitValue()
        assertThat(searchText).isEqualTo("Laptop & Mouse")
    }

    @Test
    fun `very long search text is handled`() = runTest {
        // Arrange
        val params = SharedParameters()
        viewModel.setListParams(params)

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
        val params = SharedParameters()
        viewModel.setListParams(params)

        // Act: Rapid changes
        viewModel.onTextChanged("L", 0, 0, 1)
        viewModel.onTextChanged("La", 0, 1, 1)
        viewModel.onTextChanged("Lap", 0, 2, 1)
        advanceUntilIdle()

        // Assert
        val searchText = viewModel.searchText.getOrAwaitValue()
        assertThat(searchText).isEqualTo("Lap")
    }

    @Test
    fun `group with no children shows empty list`() = runTest {
        // Arrange
        val params = SharedParameters()
        viewModel.setListParams(params)

        // Act: Navigate to group with no children
        viewModel.setCurrentGroup("empty-group")
        mockProductsFlow.value = emptyList()
        advanceUntilIdle()

        // Assert
        val products = viewModel.products.getOrAwaitValue()
        assertThat(products).isEmpty()
    }

    @Test
    fun `products with same name are all displayed`() = runTest {
        // Arrange: Multiple products with same name
        val duplicateProducts = listOf(
            createTestProducts()[0],
            createTestProducts()[0].copy(guid = "product-dup-1"),
            createTestProducts()[0].copy(guid = "product-dup-2")
        )

        val params = SharedParameters()
        viewModel.setListParams(params)
        mockProductsFlow.value = duplicateProducts
        advanceUntilIdle()

        // Assert
        val products = viewModel.products.getOrAwaitValue()
        assertThat(products).hasSize(3)
        assertThat(products.all { it.description == "Laptop ABC" }).isTrue()
    }

    @Test
    fun `product with zero price displays correctly`() = runTest {
        // Arrange
        val freeProduct = createTestProducts()[0].copy(price = 0.0)
        val params = SharedParameters()

        viewModel.setListParams(params)
        mockProductsFlow.value = listOf(freeProduct)
        advanceUntilIdle()

        // Assert
        val products = viewModel.products.getOrAwaitValue()
        assertThat(products[0].price).isEqualTo(0.0)
    }

    @Test
    fun `product with zero rest displays correctly`() = runTest {
        // Arrange
        val outOfStock = createTestProducts()[0].copy(rest = 0.0)
        val params = SharedParameters()

        viewModel.setListParams(params)
        mockProductsFlow.value = listOf(outOfStock)
        advanceUntilIdle()

        // Assert
        val products = viewModel.products.getOrAwaitValue()
        assertThat(products[0].rest).isEqualTo(0.0)
    }

    @Test
    fun `multiple search visibility toggles work correctly`() = runTest {
        // Act & Assert: Multiple toggles
        viewModel.toggleSearchVisibility()
        assertThat(viewModel.searchVisibility.getOrAwaitValue()).isEqualTo(View.VISIBLE)

        viewModel.toggleSearchVisibility()
        assertThat(viewModel.searchVisibility.getOrAwaitValue()).isEqualTo(View.GONE)

        viewModel.toggleSearchVisibility()
        assertThat(viewModel.searchVisibility.getOrAwaitValue()).isEqualTo(View.VISIBLE)
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

        mockProductsFlow.value = createTestProducts()

        // Act
        viewModel.setListParams(fullParams)
        advanceUntilIdle()

        // Assert
        val products = viewModel.products.getOrAwaitValue()
        assertThat(products).hasSize(3)
    }

    @Test
    fun `product groups (isGroup=1) can be listed`() = runTest {
        // Arrange
        val groups = listOf(
            createGroupProducts()[0],
            createGroupProducts()[0].copy(guid = "group-2", description = "Accessories")
        )

        val params = SharedParameters()
        viewModel.setListParams(params)
        mockProductsFlow.value = groups
        advanceUntilIdle()

        // Assert
        val products = viewModel.products.getOrAwaitValue()
        assertThat(products).hasSize(2)
        assertThat(products.all { it.isGroup }).isTrue()
    }
}
