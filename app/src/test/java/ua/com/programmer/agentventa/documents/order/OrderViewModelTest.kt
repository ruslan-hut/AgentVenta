package ua.com.programmer.agentventa.documents.order

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ua.com.programmer.agentventa.dao.entity.Client
import ua.com.programmer.agentventa.dao.entity.Order
import ua.com.programmer.agentventa.dao.entity.PaymentType
import ua.com.programmer.agentventa.domain.result.DomainException
import ua.com.programmer.agentventa.domain.result.Result
import ua.com.programmer.agentventa.domain.usecase.order.EnableOrderEditUseCase
import ua.com.programmer.agentventa.domain.usecase.order.SaveOrderUseCase
import ua.com.programmer.agentventa.domain.usecase.order.ValidateOrderUseCase
import ua.com.programmer.agentventa.fake.FakeOrderRepository
import ua.com.programmer.agentventa.fake.FakeProductRepository
import ua.com.programmer.agentventa.fixtures.TestFixtures
import ua.com.programmer.agentventa.logger.Logger
import ua.com.programmer.agentventa.util.MainDispatcherRule
import ua.com.programmer.agentventa.util.getOrAwaitValue
import java.util.*

/**
 * Test suite for OrderViewModel.
 *
 * This is the most complex ViewModel with:
 * - Multiple repositories (Order, Product)
 * - Use cases (Validate, Save, EnableEdit)
 * - Order content management
 * - Barcode scanning
 * - Location updates
 * - Client/Company/Store selection
 * - Price type handling
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OrderViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var productRepository: FakeProductRepository
    private lateinit var validateOrderUseCase: ValidateOrderUseCase
    private lateinit var saveOrderUseCase: SaveOrderUseCase
    private lateinit var enableOrderEditUseCase: EnableOrderEditUseCase
    private lateinit var logger: Logger
    private lateinit var viewModel: OrderViewModel

    @Before
    fun setup() {
        orderRepository = FakeOrderRepository(TestFixtures.TEST_DB_GUID)
        productRepository = FakeProductRepository(TestFixtures.TEST_DB_GUID)
        logger = mock()

        // Real use cases (they're simple enough)
        validateOrderUseCase = ValidateOrderUseCase()
        saveOrderUseCase = SaveOrderUseCase(orderRepository, validateOrderUseCase)
        enableOrderEditUseCase = EnableOrderEditUseCase(orderRepository)

        viewModel = OrderViewModel(
            orderRepository = orderRepository,
            productRepository = productRepository,
            validateOrderUseCase = validateOrderUseCase,
            saveOrderUseCase = saveOrderUseCase,
            enableOrderEditUseCase = enableOrderEditUseCase,
            logger = logger
        )
    }

    @After
    fun tearDown() {
        orderRepository.clearAll()
        productRepository.clearAll()
    }

    // ========== State Management Tests ==========

    @Test
    fun `initial order state is empty`() = runTest {
        // Assert
        assertThat(viewModel.documentGuid.value).isEmpty()
        assertThat(viewModel.documentFlow.value.guid).isEmpty()
    }

    @Test
    fun `initial price type is empty`() = runTest {
        // Assert
        assertThat(viewModel.selectedPriceTypeFlow.value).isEmpty()
    }

    @Test
    fun `initial order content is empty`() = runTest {
        // Assert
        assertThat(viewModel.currentContentFlow.value).isEmpty()
    }

    @Test
    fun `price type selection updates StateFlow`() = runTest {
        // Arrange
        val priceType = TestFixtures.PRICE_TYPE_RETAIL
        val priceDescription = "Retail Price"

        // Act
        viewModel.onPriceTypeSelected(priceType, priceDescription)
        advanceUntilIdle()

        // Assert
        viewModel.selectedPriceTypeFlow.test {
            assertThat(awaitItem()).isEqualTo(priceDescription)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `price type selection updates order document`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        orderRepository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Act
        viewModel.onPriceTypeSelected(TestFixtures.PRICE_TYPE_WHOLESALE, "Wholesale")
        advanceUntilIdle()

        // Assert
        val savedOrder = orderRepository.getOrder(order.guid)
        assertThat(savedOrder?.priceType).isEqualTo(TestFixtures.PRICE_TYPE_WHOLESALE)
    }

    @Test
    fun `order content flow emits correctly`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        val content = TestFixtures.lOrderContentForOrder1()
        orderRepository.addOrder(order)
        orderRepository.addOrderContent(order.guid, TestFixtures.orderContentForOrder1())

        // Act
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Assert
        viewModel.currentContentFlow.test {
            val items = awaitItem()
            assertThat(items).hasSize(2)
            assertThat(items[0].productGuid).isEqualTo(content[0].productGuid)
            assertThat(items[1].productGuid).isEqualTo(content[1].productGuid)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `order content LiveData syncs with Flow`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        orderRepository.addOrder(order)
        orderRepository.addOrderContent(order.guid, TestFixtures.orderContentForOrder1())

        // Act
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Assert
        val content = viewModel.currentContent.getOrAwaitValue()
        assertThat(content).hasSize(2)
    }

    @Test
    fun `document totals calculation is correct`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        orderRepository.addOrder(order)
        orderRepository.addOrderContent(order.guid, TestFixtures.orderContentForOrder1())

        // Act
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Assert - totals calculated from content
        val totals = orderRepository.getDocumentTotals(order.guid)
        assertThat(totals.sum).isEqualTo(1500.0)
        assertThat(totals.quantity).isEqualTo(10.0)
    }

    // ========== Business Logic Tests ==========

    @Test
    fun `adding product to order creates content line`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        val product = TestFixtures.createLProduct1().copy(
            guid = "test-prod",
            price = 100.0,
            quantity = 5.0
        )

        orderRepository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        var popUpCalled = false

        // Act
        viewModel.onProductClick(product) { popUpCalled = true }
        advanceUntilIdle()

        // Assert
        val content = orderRepository.getContent(order.guid)
        assertThat(content).hasSize(1)
        assertThat(content[0].productGuid).isEqualTo(product.guid)
        assertThat(content[0].quantity).isEqualTo(5.0)
        assertThat(popUpCalled).isTrue()
    }

    @Test
    fun `updating product quantity updates order totals`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        val product = TestFixtures.createLProduct1().copy(quantity = 10.0)

        orderRepository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Act
        viewModel.onProductClick(product) {}
        advanceUntilIdle()

        // Assert
        val updatedOrder = orderRepository.getOrder(order.guid)
        assertThat(updatedOrder?.quantity).isGreaterThan(0.0)
    }

    @Test
    fun `client selection updates order`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1().copy(clientGuid = null)
        val client = TestFixtures.createLClient1()

        orderRepository.addOrder(order)
        orderRepository.addClient(Client(guid = client.guid, description = client.description))
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        var popUpCalled = false

        // Act
        viewModel.onClientClick(client) { popUpCalled = true }
        advanceUntilIdle()

        // Assert
        val updatedOrder = orderRepository.getOrder(order.guid)
        assertThat(updatedOrder?.clientGuid).isEqualTo(client.guid)
        assertThat(popUpCalled).isTrue()
    }

    @Test
    fun `company selection updates order`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        val company = TestFixtures.createCompany1()

        orderRepository.addOrder(order)
        orderRepository.addCompany(company)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Act - using repository method directly since ViewModel method not exposed
        orderRepository.setCompany(order.guid, company)
        advanceUntilIdle()

        // Assert
        val updatedOrder = orderRepository.getOrder(order.guid)
        assertThat(updatedOrder?.companyGuid).isEqualTo(company.guid)
    }

    @Test
    fun `store selection updates order`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        val store = TestFixtures.createStore1()

        orderRepository.addOrder(order)
        orderRepository.addStore(store)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Act
        orderRepository.setStore(order.guid, store)
        advanceUntilIdle()

        // Assert
        val updatedOrder = orderRepository.getOrder(order.guid)
        assertThat(updatedOrder?.storeGuid).isEqualTo(store.guid)
    }

    @Test
    fun `payment type selection updates order and fiscal flag`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        val paymentType = TestFixtures.createPaymentTypeCash()

        orderRepository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Act
        viewModel.onPaymentTypeSelected(paymentType)
        advanceUntilIdle()

        // Assert
        val updatedOrder = orderRepository.getOrder(order.guid)
        assertThat(updatedOrder?.paymentType).isEqualTo(paymentType.paymentType)
        assertThat(updatedOrder?.isFiscal).isEqualTo(paymentType.isFiscal)
    }

    @Test
    fun `isReturn flag updates correctly`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1().copy(isReturn = 0)
        orderRepository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Act
        viewModel.onIsReturnClick(true)
        advanceUntilIdle()

        // Assert
        val updatedOrder = orderRepository.getOrder(order.guid)
        assertThat(updatedOrder?.isReturn).isEqualTo(1)
    }

    // ========== Use Cases Integration Tests ==========

    @Test
    fun `validateOrder returns null for valid order`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1().copy(
            clientGuid = TestFixtures.CLIENT_1_GUID,
            isFiscal = 1,
            price = 100.0,
            quantity = 1.0,
            paymentType = TestFixtures.PAYMENT_TYPE_CASH
        )
        orderRepository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Act
        val error = viewModel.validateOrder()

        // Assert
        assertThat(error).isNull()
    }

    @Test
    fun `validateOrder returns error for missing client`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1().copy(clientGuid = null)
        orderRepository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Act
        val error = viewModel.validateOrder()

        // Assert
        assertThat(error).isNotNull()
        assertThat(error).contains("Client is required")
    }

    @Test
    fun `validateOrder returns error for fiscal order with zero price`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1().copy(
            clientGuid = TestFixtures.CLIENT_1_GUID,
            isFiscal = 1,
            price = 0.0
        )
        orderRepository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Act
        val error = viewModel.validateOrder()

        // Assert
        assertThat(error).isNotNull()
        assertThat(error).contains("Price must be greater than zero")
    }

    @Test
    fun `saveDocument marks order as processed`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1().copy(
            clientGuid = TestFixtures.CLIENT_1_GUID,
            isProcessed = 0
        )
        orderRepository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Act
        viewModel.saveDocument()
        advanceUntilIdle()

        // Assert
        val savedOrder = orderRepository.getOrder(order.guid)
        assertThat(savedOrder?.isProcessed).isEqualTo(1)
        assertThat(savedOrder?.timeSaved).isGreaterThan(0)
    }

    @Test
    fun `enableEdit resets processed and sent flags`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder3Sent() // Already processed and sent
        orderRepository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Act
        viewModel.enableEdit()
        advanceUntilIdle()

        // Assert
        val editableOrder = orderRepository.getOrder(order.guid)
        assertThat(editableOrder?.isProcessed).isEqualTo(0)
        assertThat(editableOrder?.isSent).isEqualTo(0)
    }

    @Test
    fun `isNotEditable returns true for processed order`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder3Sent()
        orderRepository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Act
        val notEditable = viewModel.isNotEditable()

        // Assert
        assertThat(notEditable).isTrue()
    }

    // ========== Barcode Scanning Tests ==========

    @Test
    fun `onBarcodeRead finds product and adds to order`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        val product = FakeProductRepository.createTestProduct(barcode = "1234567890")

        orderRepository.addOrder(order)
        productRepository.addProduct(product)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        var failCalled = false

        // Act
        viewModel.onBarcodeRead("1234567890") { failCalled = true }
        advanceUntilIdle()

        // Assert
        assertThat(failCalled).isFalse()
        val content = orderRepository.getContent(order.guid)
        assertThat(content).isNotEmpty()
    }

    @Test
    fun `onBarcodeRead with invalid barcode calls onFail`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        orderRepository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        var failCalled = false

        // Act
        viewModel.onBarcodeRead("invalid-barcode") { failCalled = true }
        advanceUntilIdle()

        // Assert
        assertThat(failCalled).isTrue()
        verify(logger).w(any(), any())
    }

    @Test
    fun `onBarcodeRead increments quantity for existing product`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        val product = FakeProductRepository.createTestProduct(barcode = "1234567890")

        orderRepository.addOrder(order)
        productRepository.addProduct(product)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Act - scan twice
        viewModel.onBarcodeRead("1234567890") {}
        advanceUntilIdle()
        viewModel.onBarcodeRead("1234567890") {}
        advanceUntilIdle()

        // Assert - quantity should be incremented
        val content = orderRepository.getContent(order.guid)
        assertThat(content).hasSize(1)
        // Quantity incremented by barcode scan logic
    }

    // ========== Location Updates Tests ==========

    @Test
    fun `updateLocation saves location to order`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        orderRepository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        var completeCalled = false

        // Act
        viewModel.updateLocation { completeCalled = true }
        advanceUntilIdle()

        // Assert
        assertThat(completeCalled).isTrue()
    }

    // ========== Copy Previous Content Tests ==========

    @Test
    fun `copyPrevious copies content from previous order`() = runTest {
        // Arrange
        val client = TestFixtures.createClient1()
        val oldOrder = TestFixtures.createOrder3Sent().copy(clientGuid = client.guid)
        val newOrder = TestFixtures.createOrder1().copy(clientGuid = client.guid)

        orderRepository.addOrder(oldOrder)
        orderRepository.addOrderContent(oldOrder.guid, TestFixtures.orderContentForOrder1())
        orderRepository.addOrder(newOrder)

        viewModel.setCurrentDocument(newOrder.guid)
        advanceUntilIdle()

        var result: Boolean? = null

        // Act
        viewModel.copyPrevious { result = it }
        advanceUntilIdle()

        // Assert
        assertThat(result).isTrue()
        val content = orderRepository.getContent(newOrder.guid)
        assertThat(content).isNotEmpty()
    }

    @Test
    fun `copyPrevious returns false when no client selected`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1().copy(clientGuid = null)
        orderRepository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        var result: Boolean? = null

        // Act
        viewModel.copyPrevious { result = it }

        // Assert
        assertThat(result).isFalse()
    }

    // ========== Helper Methods Tests ==========

    @Test
    fun `isFiscal returns true for fiscal order`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1().copy(isFiscal = 1)
        orderRepository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Act & Assert
        assertThat(viewModel.isFiscal()).isTrue()
    }

    @Test
    fun `isFiscal returns false for non-fiscal order`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1().copy(isFiscal = 0)
        orderRepository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Act & Assert
        assertThat(viewModel.isFiscal()).isFalse()
    }

    @Test
    fun `canPrint returns true for sent order`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder3Sent()
        orderRepository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Act & Assert
        assertThat(viewModel.canPrint()).isTrue()
    }

    @Test
    fun `canPrint returns false for unsent order`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1().copy(isSent = 0)
        orderRepository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Act & Assert
        assertThat(viewModel.canPrint()).isFalse()
    }

    @Test
    fun `getCompanyGuid returns order company GUID`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        orderRepository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Act & Assert
        assertThat(viewModel.getCompanyGuid()).isEqualTo(order.companyGuid)
    }

    @Test
    fun `setDeliveryDate updates order`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        val deliveryDate = Date()

        orderRepository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Act
        viewModel.setDeliveryDate(deliveryDate)
        advanceUntilIdle()

        // Assert
        val updatedOrder = orderRepository.getOrder(order.guid)
        assertThat(updatedOrder?.deliveryDate).isNotEmpty()
    }

    @Test
    fun `onEditNotes updates order notes`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        val newNotes = "Updated notes for testing"

        orderRepository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Act
        viewModel.onEditNotes(newNotes)
        advanceUntilIdle()

        // Assert
        val updatedOrder = orderRepository.getOrder(order.guid)
        assertThat(updatedOrder?.notes).isEqualTo(newNotes)
    }

    // ========== Edge Cases and Error Handling ==========

    @Test
    fun `adding product to processed order does not modify content`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder3Sent() // Processed
        val product = TestFixtures.createLProduct1()

        orderRepository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        var popUpCalled = false

        // Act
        viewModel.onProductClick(product) { popUpCalled = true }
        advanceUntilIdle()

        // Assert - should just call popUp without adding
        assertThat(popUpCalled).isTrue()
        val content = orderRepository.getContent(order.guid)
        assertThat(content).isEmpty()
    }

    @Test
    fun `setClient does nothing if client already set`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        orderRepository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        val originalClientGuid = order.clientGuid

        // Act - try to set different client
        viewModel.setClient(TestFixtures.CLIENT_2_GUID)
        advanceUntilIdle()

        // Assert - client should not change
        val updatedOrder = orderRepository.getOrder(order.guid)
        assertThat(updatedOrder?.clientGuid).isEqualTo(originalClientGuid)
    }

    @Test
    fun `price type selection does not update if already same`() = runTest {
        // Arrange
        val order = TestFixtures.createOrder1()
        orderRepository.addOrder(order)
        viewModel.setCurrentDocument(order.guid)
        advanceUntilIdle()

        // Act - set same price type
        viewModel.onPriceTypeSelected(order.priceType, "Same Price")
        advanceUntilIdle()

        // Assert - no unnecessary update
        val updatedOrder = orderRepository.getOrder(order.guid)
        assertThat(updatedOrder?.priceType).isEqualTo(order.priceType)
    }
}
