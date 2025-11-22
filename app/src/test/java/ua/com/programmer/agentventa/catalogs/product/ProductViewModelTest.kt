package ua.com.programmer.agentventa.catalogs.product

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
import ua.com.programmer.agentventa.dao.entity.LPrice
import ua.com.programmer.agentventa.dao.entity.LProduct
import ua.com.programmer.agentventa.fixtures.TestFixtures
import ua.com.programmer.agentventa.repository.ProductRepository
import ua.com.programmer.agentventa.util.MainDispatcherRule

/**
 * Test suite for ProductViewModel
 *
 * Covers:
 * - Product details loading with price type and order context
 * - Price list loading for product
 * - Parameters handling (guid, orderGuid, priceType)
 * - Price selection event emission
 * - Reactive state updates
 * - Edge cases and error handling
 */
class ProductViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var productRepository: ProductRepository
    private lateinit var viewModel: ProductViewModel

    // Mock flows
    private lateinit var mockProductFlow: MutableStateFlow<LProduct>
    private lateinit var mockPriceListFlow: MutableStateFlow<List<LPrice>>

    @Before
    fun setup() {
        // Initialize mock flows
        // Default empty product for non-nullable flow
        val defaultProduct = LProduct(
            guid = "",
            description = "",
            code = "",
            vendorCode = "",
            isGroup = false
        )
        mockProductFlow = MutableStateFlow(defaultProduct)
        mockPriceListFlow = MutableStateFlow(emptyList())

        // Mock repository
        productRepository = mock {
            on { getProduct(any(), any(), any()) } doReturn mockProductFlow
            on { fetchProductPrices(any(), any()) } doReturn mockPriceListFlow
        }

        viewModel = ProductViewModel(productRepository = productRepository)
    }

    // ========================================
    // Helper Methods
    // ========================================

    private fun createTestProduct(
        guid: String = "product-1",
        description: String = "Test Product",
        price: Double = 100.0
    ) = LProduct(
        guid = guid,
        description = description,
        vendorCode = "VND001",
        price = price,
        quantity = 50.0,
        rest = 100.0,
        isGroup = false,
        code = "P001"
    )

    private fun createTestPrices() = listOf(
        LPrice(
            priceType = "P1",
            description = "Retail",
            price = 100.0,
            basePrice = 90.0,
            isCurrent = true
        ),
        LPrice(
            priceType = "P2",
            description = "Wholesale",
            price = 80.0,
            basePrice = 70.0,
            isCurrent = false
        ),
        LPrice(
            priceType = "P3",
            description = "VIP",
            price = 90.0,
            basePrice = 80.0,
            isCurrent = false
        )
    )

    // ========================================
    // Initial State Tests
    // ========================================

    @Test
    fun `initial product is empty`() = runTest {
        viewModel.productFlow.test {
            val product = awaitItem()
            assertThat(product?.guid).isEmpty()
            assertThat(product?.description).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial price list is empty`() = runTest {
        viewModel.priceListFlow.test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // Product Loading Tests
    // ========================================

    @Test
    fun `setProductParameters loads product from repository`() = runTest {
        // Arrange
        val product = createTestProduct()
        mockProductFlow.value = product

        // Act
        viewModel.setProductParameters(
            guid = product.guid,
            orderGuid = "order-1",
            priceType = "P1"
        )
        advanceUntilIdle()

        // Assert
        viewModel.productFlow.test {
            val loadedProduct = awaitItem()
            assertThat(loadedProduct).isNotNull()
            assertThat(loadedProduct?.guid).isEqualTo(product.guid)
            assertThat(loadedProduct?.description).isEqualTo(product.description)
            cancelAndIgnoreRemainingEvents()
        }

        verify(productRepository).getProduct(product.guid, "order-1", "P1")
    }

    @Test
    fun `product updates when repository emits new value`() = runTest {
        // Arrange
        val product1 = createTestProduct("product-1", "Original Product")
        viewModel.setProductParameters(product1.guid, "", "")

        viewModel.productFlow.test {
            // Initial null
            skipItems(1)

            // First product
            mockProductFlow.value = product1
            var product = awaitItem()
            assertThat(product?.description).isEqualTo("Original Product")

            // Updated product
            val product2 = product1.copy(description = "Updated Product")
            mockProductFlow.value = product2
            product = awaitItem()
            assertThat(product?.description).isEqualTo("Updated Product")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setProductParameters with empty GUID keeps product null`() = runTest {
        // Act
        viewModel.setProductParameters("", "", "")
        advanceUntilIdle()

        // Assert
        viewModel.productFlow.test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `product loading with order context uses orderGuid`() = runTest {
        // Arrange
        val product = createTestProduct()
        mockProductFlow.value = product

        // Act
        viewModel.setProductParameters(product.guid, "order-123", "P1")
        advanceUntilIdle()

        // Assert
        verify(productRepository).getProduct(product.guid, "order-123", "P1")
    }

    @Test
    fun `product loading with price type uses priceType`() = runTest {
        // Arrange
        val product = createTestProduct()
        mockProductFlow.value = product

        // Act
        viewModel.setProductParameters(product.guid, "", "P2")
        advanceUntilIdle()

        // Assert
        verify(productRepository).getProduct(product.guid, "", "P2")
    }

    // ========================================
    // Price List Loading Tests
    // ========================================

    @Test
    fun `price list loads when product GUID is set`() = runTest {
        // Arrange
        val prices = createTestPrices()
        mockPriceListFlow.value = prices

        // Act
        viewModel.setProductParameters("product-1", "", "P1")
        advanceUntilIdle()

        // Assert
        viewModel.priceListFlow.test {
            val loadedPrices = awaitItem()
            assertThat(loadedPrices).hasSize(3)
            assertThat(loadedPrices[0].description).isEqualTo("Retail")
            assertThat(loadedPrices[1].description).isEqualTo("Wholesale")
            assertThat(loadedPrices[2].description).isEqualTo("VIP")
            cancelAndIgnoreRemainingEvents()
        }

        verify(productRepository).fetchProductPrices("product-1", "P1")
    }

    @Test
    fun `price list updates when repository emits new values`() = runTest {
        // Arrange
        viewModel.setProductParameters("product-1", "", "P1")

        viewModel.priceListFlow.test {
            // Initial empty
            var prices = awaitItem()
            assertThat(prices).isEmpty()

            // Add prices
            mockPriceListFlow.value = createTestPrices()
            prices = awaitItem()
            assertThat(prices).hasSize(3)

            // Update prices
            mockPriceListFlow.value = createTestPrices().subList(0, 1)
            prices = awaitItem()
            assertThat(prices).hasSize(1)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `price list is empty when product GUID is empty`() = runTest {
        // Act
        viewModel.setProductParameters("", "", "")
        advanceUntilIdle()

        // Assert
        viewModel.priceListFlow.test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `price list respects price type parameter`() = runTest {
        // Arrange
        mockPriceListFlow.value = createTestPrices()

        // Act
        viewModel.setProductParameters("product-1", "", "P2")
        advanceUntilIdle()

        // Assert
        verify(productRepository).fetchProductPrices("product-1", "P2")
    }

    // ========================================
    // Parameters Tests
    // ========================================

    @Test
    fun `changing parameters reloads product and prices`() = runTest {
        // Arrange: First parameters
        val product1 = createTestProduct("product-1")
        viewModel.setProductParameters("product-1", "order-1", "P1")
        mockProductFlow.value = product1
        advanceUntilIdle()

        // Act: Change parameters
        val product2 = createTestProduct("product-2")
        viewModel.setProductParameters("product-2", "order-2", "P2")
        mockProductFlow.value = product2
        advanceUntilIdle()

        // Assert: New repository calls
        verify(productRepository).getProduct("product-2", "order-2", "P2")
        verify(productRepository).fetchProductPrices("product-2", "P2")
    }

    @Test
    fun `parameters with all fields set work correctly`() = runTest {
        // Arrange
        val product = createTestProduct()
        mockProductFlow.value = product

        // Act
        viewModel.setProductParameters(
            guid = "product-123",
            orderGuid = "order-456",
            priceType = "P3"
        )
        advanceUntilIdle()

        // Assert
        verify(productRepository).getProduct("product-123", "order-456", "P3")
        verify(productRepository).fetchProductPrices("product-123", "P3")
    }

    // ========================================
    // Price Selection Event Tests
    // ========================================

    @Test
    fun `onPriceSelected emits PriceSelected event`() = runTest {
        // Arrange
        val price = createTestPrices()[0]

        // Act & Assert
        viewModel.events.test {
            viewModel.onPriceSelected(price)

            val event = awaitItem()
            assertThat(event).isInstanceOf(ProductEvent.PriceSelected::class.java)
            assertThat((event as ProductEvent.PriceSelected).price).isEqualTo(price)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onPriceSelected with different prices emits correct events`() = runTest {
        // Arrange
        val prices = createTestPrices()

        // Act & Assert
        viewModel.events.test {
            viewModel.onPriceSelected(prices[0])
            var event = awaitItem()
            assertThat((event as ProductEvent.PriceSelected).price.description)
                .isEqualTo("Retail")

            viewModel.onPriceSelected(prices[1])
            event = awaitItem()
            assertThat((event as ProductEvent.PriceSelected).price.description)
                .isEqualTo("Wholesale")

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // Combined State Tests
    // ========================================

    @Test
    fun `product and price list load simultaneously`() = runTest {
        // Arrange
        val product = createTestProduct()
        val prices = createTestPrices()

        // Act
        viewModel.setProductParameters(product.guid, "", "P1")
        mockProductFlow.value = product
        mockPriceListFlow.value = prices
        advanceUntilIdle()

        // Assert: Both flows have data
        viewModel.productFlow.test {
            assertThat(awaitItem()?.guid).isEqualTo(product.guid)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.priceListFlow.test {
            assertThat(awaitItem()).hasSize(3)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching products updates both product and prices`() = runTest {
        // Arrange: First product
        val product1 = createTestProduct("product-1")
        viewModel.setProductParameters(product1.guid, "", "P1")
        mockProductFlow.value = product1
        mockPriceListFlow.value = createTestPrices()
        advanceUntilIdle()

        // Act: Switch to second product
        val product2 = createTestProduct("product-2")
        viewModel.setProductParameters(product2.guid, "", "P1")
        mockProductFlow.value = product2
        mockPriceListFlow.value = emptyList()
        advanceUntilIdle()

        // Assert
        viewModel.productFlow.test {
            assertThat(awaitItem()?.guid).isEqualTo("product-2")
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.priceListFlow.test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // Edge Cases and Error Handling Tests
    // ========================================

    @Test
    fun `product with no prices shows empty price list`() = runTest {
        // Arrange
        val product = createTestProduct()
        mockProductFlow.value = product
        mockPriceListFlow.value = emptyList()

        // Act
        viewModel.setProductParameters(product.guid, "", "P1")
        advanceUntilIdle()

        // Assert
        viewModel.priceListFlow.test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `large price list is handled correctly`() = runTest {
        // Arrange: 10 different prices
        val largePriceList = (1..10).map { index ->
            LPrice(
                priceType = "P$index",
                description = "Price Type $index",
                price = index.toDouble() * 10,
                basePrice = index.toDouble() * 8,
                isCurrent = index == 1
            )
        }

        mockPriceListFlow.value = largePriceList
        viewModel.setProductParameters("product-1", "", "P1")
        advanceUntilIdle()

        // Assert
        viewModel.priceListFlow.test {
            val prices = awaitItem()
            assertThat(prices).hasSize(10)
            assertThat(prices.first().description).isEqualTo("Price Type 1")
            assertThat(prices.last().description).isEqualTo("Price Type 10")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `product with all fields populated loads correctly`() = runTest {
        // Arrange
        val fullProduct = LProduct(
            guid = "full-product",
            description = "Full Product Description",
            vendorCode = "VND-FULL-001",
            price = 250.50,
            quantity = 75.0,
            rest = 200.0,
            isGroup = false,
            code = "FULL001",
            unit = "pcs",
            unitType = "piece",
            groupName = "Electronics",
            weight = 1.5,
            priceType = "Retail",
            basePrice = 200.0,
            minPrice = 180.0
        )

        mockProductFlow.value = fullProduct

        // Act
        viewModel.setProductParameters(fullProduct.guid, "", "P1")
        advanceUntilIdle()

        // Assert
        viewModel.productFlow.test {
            val product = awaitItem()
            assertThat(product?.guid).isEqualTo("full-product")
            assertThat(product?.description).isEqualTo("Full Product Description")
            assertThat(product?.vendorCode).isEqualTo("VND-FULL-001")
            assertThat(product?.price).isEqualTo(250.50)
            assertThat(product?.quantity).isEqualTo(75.0)
            assertThat(product?.rest).isEqualTo(200.0)
            assertThat(product?.unit).isEqualTo("pcs")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `multiple parameter updates emit correctly`() = runTest {
        viewModel.productFlow.test {
            skipItems(1) // Skip initial

            // Rapid parameter changes
            viewModel.setProductParameters("p1", "", "")
            viewModel.setProductParameters("p2", "", "")
            viewModel.setProductParameters("p3", "", "")

            // All should be null since we didn't update flows
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setting same parameters multiple times works correctly`() = runTest {
        // Arrange
        val product = createTestProduct()
        mockProductFlow.value = product

        // Act: Set same parameters multiple times
        viewModel.setProductParameters(product.guid, "order-1", "P1")
        viewModel.setProductParameters(product.guid, "order-1", "P1")
        viewModel.setProductParameters(product.guid, "order-1", "P1")
        advanceUntilIdle()

        // Assert: Product loaded correctly
        viewModel.productFlow.test {
            assertThat(awaitItem()?.guid).isEqualTo(product.guid)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `price selection with zero price works`() = runTest {
        // Arrange
        val zeroPrice = LPrice(
            priceType = "P1",
            description = "Free",
            price = 0.0,
            basePrice = 0.0,
            isCurrent = true
        )

        // Act & Assert
        viewModel.events.test {
            viewModel.onPriceSelected(zeroPrice)

            val event = awaitItem()
            assertThat((event as ProductEvent.PriceSelected).price.price).isEqualTo(0.0)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `price selection with negative price works`() = runTest {
        // Arrange
        val negativePrice = LPrice(
            priceType = "P1",
            description = "Discount",
            price = -10.0,
            basePrice = 0.0,
            isCurrent = true
        )

        // Act & Assert
        viewModel.events.test {
            viewModel.onPriceSelected(negativePrice)

            val event = awaitItem()
            assertThat((event as ProductEvent.PriceSelected).price.price).isEqualTo(-10.0)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `product group (isGroup=1) loads correctly`() = runTest {
        // Arrange
        val productGroup = createTestProduct().copy(
            isGroup = true,
            description = "Product Category"
        )
        mockProductFlow.value = productGroup

        // Act
        viewModel.setProductParameters(productGroup.guid, "", "")
        advanceUntilIdle()

        // Assert
        viewModel.productFlow.test {
            val product = awaitItem()
            assertThat(product?.isGroup).isEqualTo(1)
            assertThat(product?.description).isEqualTo("Product Category")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `product with zero stock (rest=0) loads correctly`() = runTest {
        // Arrange
        val outOfStock = createTestProduct().copy(rest = 0.0)
        mockProductFlow.value = outOfStock

        // Act
        viewModel.setProductParameters(outOfStock.guid, "", "")
        advanceUntilIdle()

        // Assert
        viewModel.productFlow.test {
            assertThat(awaitItem()?.rest).isEqualTo(0.0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty orderGuid and priceType parameters work`() = runTest {
        // Arrange
        val product = createTestProduct()
        mockProductFlow.value = product

        // Act
        viewModel.setProductParameters(product.guid, "", "")
        advanceUntilIdle()

        // Assert
        verify(productRepository).getProduct(product.guid, "", "")
        verify(productRepository).fetchProductPrices(product.guid, "")
    }
}
