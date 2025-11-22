package ua.com.programmer.agentventa.catalogs.product

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
import org.mockito.kotlin.verify
import ua.com.programmer.agentventa.dao.entity.LProduct
import ua.com.programmer.agentventa.fixtures.TestFixtures
import ua.com.programmer.agentventa.repository.ProductRepository
import ua.com.programmer.agentventa.util.MainDispatcherRule
import ua.com.programmer.agentventa.util.getOrAwaitValue

/**
 * Test suite for ProductImageViewModel
 *
 * Covers:
 * - Product loading by GUID for image display
 * - Product state management
 * - Reactive updates from repository
 * - Edge cases and error handling
 *
 * Note: This is a simple ViewModel that only loads product details
 * for image display purposes. It doesn't handle image CRUD operations.
 */
class ProductImageViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var productRepository: ProductRepository
    private lateinit var viewModel: ProductImageViewModel

    // Mock flow
    private lateinit var mockProductFlow: MutableStateFlow<LProduct?>

    @Before
    fun setup() {
        // Initialize mock flow
        mockProductFlow = MutableStateFlow(null)

        // Mock repository
        productRepository = mock {
            on { getProduct(any()) } doReturn mockProductFlow
        }

        viewModel = ProductImageViewModel(productRepository = productRepository)
    }

    // ========================================
    // Helper Methods
    // ========================================

    private fun createTestProduct(
        guid: String = "product-1",
        description: String = "Test Product"
    ) = LProduct(
        guid = guid,
        description = description,
        barcode = "1234567890",
        article = "ART001",
        price = 100.0,
        quantity = 50.0,
        rest = 100.0,
        db_guid = TestFixtures.TEST_DB_GUID,
        isGroup = 0,
        parentGuid = "",
        code = "P001"
    )

    // ========================================
    // Initial State Tests
    // ========================================

    @Test
    fun `initial product is null`() = runTest {
        // Assert
        val product = viewModel.product.value
        assertThat(product).isNull()
    }

    // ========================================
    // Product Loading Tests
    // ========================================

    @Test
    fun `setProductParameters loads product from repository`() = runTest {
        // Arrange
        val testProduct = createTestProduct()
        mockProductFlow.value = testProduct

        // Act
        viewModel.setProductParameters(testProduct.guid)
        advanceUntilIdle()

        // Assert
        val loadedProduct = viewModel.product.getOrAwaitValue()
        assertThat(loadedProduct).isNotNull()
        assertThat(loadedProduct.guid).isEqualTo(testProduct.guid)
        assertThat(loadedProduct.description).isEqualTo(testProduct.description)

        verify(productRepository).getProduct(testProduct.guid)
    }

    @Test
    fun `product updates when repository emits new value`() = runTest {
        // Arrange
        val product1 = createTestProduct("product-1", "Original Product")
        viewModel.setProductParameters(product1.guid)
        mockProductFlow.value = product1
        advanceUntilIdle()

        // Act: Update product
        val product2 = createTestProduct("product-1", "Updated Product")
        mockProductFlow.value = product2
        advanceUntilIdle()

        // Assert
        val loadedProduct = viewModel.product.getOrAwaitValue()
        assertThat(loadedProduct.description).isEqualTo("Updated Product")
    }

    @Test
    fun `loading different product updates state`() = runTest {
        // Arrange: First product
        val product1 = createTestProduct("product-1")
        viewModel.setProductParameters(product1.guid)
        mockProductFlow.value = product1
        advanceUntilIdle()

        // Act: Load different product
        val product2 = createTestProduct("product-2")
        viewModel.setProductParameters(product2.guid)
        mockProductFlow.value = product2
        advanceUntilIdle()

        // Assert
        val loadedProduct = viewModel.product.getOrAwaitValue()
        assertThat(loadedProduct.guid).isEqualTo("product-2")

        verify(productRepository).getProduct("product-2")
    }

    @Test
    fun `product with null from repository updates to null`() = runTest {
        // Arrange: Start with a product
        val product = createTestProduct()
        viewModel.setProductParameters(product.guid)
        mockProductFlow.value = product
        advanceUntilIdle()

        // Act: Repository returns null
        mockProductFlow.value = null
        advanceUntilIdle()

        // Assert
        val loadedProduct = viewModel.product.value
        assertThat(loadedProduct).isNull()
    }

    // ========================================
    // Product Field Tests
    // ========================================

    @Test
    fun `product with all fields populated loads correctly`() = runTest {
        // Arrange
        val fullProduct = LProduct(
            guid = "full-product",
            description = "Full Product Description",
            barcode = "9876543210",
            article = "ART-FULL-001",
            price = 250.50,
            quantity = 75.0,
            rest = 200.0,
            db_guid = TestFixtures.TEST_DB_GUID,
            isGroup = 0,
            parentGuid = "parent-group",
            code = "FULL001"
        )

        mockProductFlow.value = fullProduct

        // Act
        viewModel.setProductParameters(fullProduct.guid)
        advanceUntilIdle()

        // Assert
        val product = viewModel.product.getOrAwaitValue()
        assertThat(product.guid).isEqualTo("full-product")
        assertThat(product.description).isEqualTo("Full Product Description")
        assertThat(product.barcode).isEqualTo("9876543210")
        assertThat(product.article).isEqualTo("ART-FULL-001")
        assertThat(product.price).isEqualTo(250.50)
        assertThat(product.quantity).isEqualTo(75.0)
        assertThat(product.rest).isEqualTo(200.0)
        assertThat(product.code).isEqualTo("FULL001")
    }

    @Test
    fun `product with empty barcode loads correctly`() = runTest {
        // Arrange
        val productNoBarcode = createTestProduct().copy(barcode = "")
        mockProductFlow.value = productNoBarcode

        // Act
        viewModel.setProductParameters(productNoBarcode.guid)
        advanceUntilIdle()

        // Assert
        val product = viewModel.product.getOrAwaitValue()
        assertThat(product.barcode).isEmpty()
    }

    @Test
    fun `product with zero price loads correctly`() = runTest {
        // Arrange
        val freeProduct = createTestProduct().copy(price = 0.0)
        mockProductFlow.value = freeProduct

        // Act
        viewModel.setProductParameters(freeProduct.guid)
        advanceUntilIdle()

        // Assert
        val product = viewModel.product.getOrAwaitValue()
        assertThat(product.price).isEqualTo(0.0)
    }

    @Test
    fun `product with zero rest (out of stock) loads correctly`() = runTest {
        // Arrange
        val outOfStock = createTestProduct().copy(rest = 0.0)
        mockProductFlow.value = outOfStock

        // Act
        viewModel.setProductParameters(outOfStock.guid)
        advanceUntilIdle()

        // Assert
        val product = viewModel.product.getOrAwaitValue()
        assertThat(product.rest).isEqualTo(0.0)
    }

    @Test
    fun `product group (isGroup=1) loads correctly`() = runTest {
        // Arrange
        val productGroup = createTestProduct().copy(
            isGroup = 1,
            description = "Product Category"
        )
        mockProductFlow.value = productGroup

        // Act
        viewModel.setProductParameters(productGroup.guid)
        advanceUntilIdle()

        // Assert
        val product = viewModel.product.getOrAwaitValue()
        assertThat(product.isGroup).isEqualTo(1)
        assertThat(product.description).isEqualTo("Product Category")
    }

    // ========================================
    // Edge Cases and Error Handling Tests
    // ========================================

    @Test
    fun `setting same product GUID multiple times works correctly`() = runTest {
        // Arrange
        val product = createTestProduct()
        mockProductFlow.value = product

        // Act: Set same GUID multiple times
        viewModel.setProductParameters(product.guid)
        viewModel.setProductParameters(product.guid)
        viewModel.setProductParameters(product.guid)
        advanceUntilIdle()

        // Assert: Product loaded correctly
        val loadedProduct = viewModel.product.getOrAwaitValue()
        assertThat(loadedProduct.guid).isEqualTo(product.guid)
    }

    @Test
    fun `rapid product parameter changes work correctly`() = runTest {
        // Arrange
        val product1 = createTestProduct("p1")
        val product2 = createTestProduct("p2")
        val product3 = createTestProduct("p3")

        // Act: Rapid changes
        viewModel.setProductParameters("p1")
        viewModel.setProductParameters("p2")
        viewModel.setProductParameters("p3")
        mockProductFlow.value = product3
        advanceUntilIdle()

        // Assert: Last product loaded
        val loadedProduct = viewModel.product.getOrAwaitValue()
        assertThat(loadedProduct.guid).isEqualTo("p3")
    }

    @Test
    fun `product with very long description loads correctly`() = runTest {
        // Arrange
        val longDescription = "A".repeat(1000)
        val product = createTestProduct().copy(description = longDescription)
        mockProductFlow.value = product

        // Act
        viewModel.setProductParameters(product.guid)
        advanceUntilIdle()

        // Assert
        val loadedProduct = viewModel.product.getOrAwaitValue()
        assertThat(loadedProduct.description).hasLength(1000)
    }

    @Test
    fun `product with special characters in description loads correctly`() = runTest {
        // Arrange
        val specialDescription = "Product & Item <Main> \"Test\""
        val product = createTestProduct().copy(description = specialDescription)
        mockProductFlow.value = product

        // Act
        viewModel.setProductParameters(product.guid)
        advanceUntilIdle()

        // Assert
        val loadedProduct = viewModel.product.getOrAwaitValue()
        assertThat(loadedProduct.description).isEqualTo(specialDescription)
    }

    @Test
    fun `product with unicode characters loads correctly`() = runTest {
        // Arrange
        val unicodeDescription = "Товар 产品 🎁"
        val product = createTestProduct().copy(description = unicodeDescription)
        mockProductFlow.value = product

        // Act
        viewModel.setProductParameters(product.guid)
        advanceUntilIdle()

        // Assert
        val loadedProduct = viewModel.product.getOrAwaitValue()
        assertThat(loadedProduct.description).isEqualTo(unicodeDescription)
    }

    @Test
    fun `product with negative price loads correctly`() = runTest {
        // Arrange (edge case, but system should handle)
        val negativePrice = createTestProduct().copy(price = -10.0)
        mockProductFlow.value = negativePrice

        // Act
        viewModel.setProductParameters(negativePrice.guid)
        advanceUntilIdle()

        // Assert
        val product = viewModel.product.getOrAwaitValue()
        assertThat(product.price).isEqualTo(-10.0)
    }

    @Test
    fun `product with negative rest loads correctly`() = runTest {
        // Arrange (edge case for returns/adjustments)
        val negativeRest = createTestProduct().copy(rest = -5.0)
        mockProductFlow.value = negativeRest

        // Act
        viewModel.setProductParameters(negativeRest.guid)
        advanceUntilIdle()

        // Assert
        val product = viewModel.product.getOrAwaitValue()
        assertThat(product.rest).isEqualTo(-5.0)
    }

    @Test
    fun `product with very large quantities loads correctly`() = runTest {
        // Arrange
        val largeQuantity = createTestProduct().copy(
            quantity = 999999.99,
            rest = 888888.88
        )
        mockProductFlow.value = largeQuantity

        // Act
        viewModel.setProductParameters(largeQuantity.guid)
        advanceUntilIdle()

        // Assert
        val product = viewModel.product.getOrAwaitValue()
        assertThat(product.quantity).isEqualTo(999999.99)
        assertThat(product.rest).isEqualTo(888888.88)
    }

    @Test
    fun `product with empty article loads correctly`() = runTest {
        // Arrange
        val noArticle = createTestProduct().copy(article = "")
        mockProductFlow.value = noArticle

        // Act
        viewModel.setProductParameters(noArticle.guid)
        advanceUntilIdle()

        // Assert
        val product = viewModel.product.getOrAwaitValue()
        assertThat(product.article).isEmpty()
    }

    @Test
    fun `product with empty code loads correctly`() = runTest {
        // Arrange
        val noCode = createTestProduct().copy(code = "")
        mockProductFlow.value = noCode

        // Act
        viewModel.setProductParameters(noCode.guid)
        advanceUntilIdle()

        // Assert
        val product = viewModel.product.getOrAwaitValue()
        assertThat(product.code).isEmpty()
    }

    @Test
    fun `product with parent GUID loads correctly`() = runTest {
        // Arrange
        val childProduct = createTestProduct().copy(parentGuid = "parent-category")
        mockProductFlow.value = childProduct

        // Act
        viewModel.setProductParameters(childProduct.guid)
        advanceUntilIdle()

        // Assert
        val product = viewModel.product.getOrAwaitValue()
        assertThat(product.parentGuid).isEqualTo("parent-category")
    }

    @Test
    fun `loading product with empty GUID parameter works`() = runTest {
        // Act
        viewModel.setProductParameters("")
        advanceUntilIdle()

        // Assert: Repository should still be called
        verify(productRepository).getProduct("")
    }

    @Test
    fun `multiple rapid repository updates emit correctly`() = runTest {
        // Arrange
        viewModel.setProductParameters("product-1")

        val product1 = createTestProduct("product-1", "Version 1")
        val product2 = createTestProduct("product-1", "Version 2")
        val product3 = createTestProduct("product-1", "Version 3")

        // Act: Rapid repository updates
        mockProductFlow.value = product1
        advanceUntilIdle()

        mockProductFlow.value = product2
        advanceUntilIdle()

        mockProductFlow.value = product3
        advanceUntilIdle()

        // Assert: Final version loaded
        val loadedProduct = viewModel.product.getOrAwaitValue()
        assertThat(loadedProduct.description).isEqualTo("Version 3")
    }
}
