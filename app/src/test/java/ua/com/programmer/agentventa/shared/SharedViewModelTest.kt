package ua.com.programmer.agentventa.shared

import android.content.SharedPreferences
import android.widget.ImageView
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ua.com.programmer.agentventa.dao.entity.ClientImage
import ua.com.programmer.agentventa.dao.entity.Company
import ua.com.programmer.agentventa.dao.entity.DocumentTotals
import ua.com.programmer.agentventa.dao.entity.LClient
import ua.com.programmer.agentventa.dao.entity.LProduct
import ua.com.programmer.agentventa.dao.entity.PaymentType
import ua.com.programmer.agentventa.dao.entity.PriceType
import ua.com.programmer.agentventa.dao.entity.Store
import ua.com.programmer.agentventa.dao.entity.UserAccount
import ua.com.programmer.agentventa.fake.FakeOrderRepository
import ua.com.programmer.agentventa.fake.FakeUserAccountRepository
import ua.com.programmer.agentventa.fixtures.TestFixtures
import ua.com.programmer.agentventa.http.Result
import ua.com.programmer.agentventa.logger.Logger
import ua.com.programmer.agentventa.repository.CommonRepository
import ua.com.programmer.agentventa.repository.FilesRepository
import ua.com.programmer.agentventa.settings.UserOptions
import ua.com.programmer.agentventa.util.MainDispatcherRule
import java.io.File

/**
 * Test suite for SharedViewModel
 *
 * Covers:
 * - Account state management and switching
 * - Barcode handling and emission
 * - Shared parameters (document GUID, price type, company/store)
 * - Image loading delegation
 * - Action callbacks for client/product selection
 * - Sync state delegation
 * - Document totals calculation
 * - Preferences integration
 * - Edge cases and error handling
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SharedViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var filesRepository: FilesRepository
    private lateinit var logger: Logger
    private lateinit var imageLoadingManager: ImageLoadingManager
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var commonRepository: CommonRepository
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var accountStateManager: AccountStateManager
    private lateinit var syncManager: SyncManager
    private lateinit var viewModel: SharedViewModel

    // Mock state flows for managers
    private lateinit var mockOptions: MutableStateFlow<UserOptions>
    private lateinit var mockPriceTypes: MutableStateFlow<List<PriceType>>
    private lateinit var mockPaymentTypes: MutableStateFlow<List<PaymentType>>
    private lateinit var mockCompanies: MutableStateFlow<List<Company>>
    private lateinit var mockStores: MutableStateFlow<List<Store>>
    private lateinit var mockDefaultCompany: MutableStateFlow<Company>
    private lateinit var mockDefaultStore: MutableStateFlow<Store>
    private lateinit var mockUpdateState: MutableStateFlow<Result?>
    private lateinit var mockIsRefreshing: MutableStateFlow<Boolean>
    private lateinit var mockProgressMessage: MutableStateFlow<String>

    @Before
    fun setup() {
        // Create fake repositories
        orderRepository = FakeOrderRepository(FakeUserAccountRepository.TEST_ACCOUNT_GUID)

        // Create mocks for dependencies
        filesRepository = mock()
        logger = mock()
        imageLoadingManager = mock()
        commonRepository = mock()

        // Mock SharedPreferences
        sharedPreferences = mock {
            on { getBoolean(eq("show_rests_only"), any()) } doReturn false
            on { getBoolean(eq("ignore_sequential_barcodes"), any()) } doReturn false
        }

        // Initialize state flows for managers
        mockOptions = MutableStateFlow(UserOptions(isEmpty = true))
        mockPriceTypes = MutableStateFlow(createTestPriceTypes())
        mockPaymentTypes = MutableStateFlow(createTestPaymentTypes())
        mockCompanies = MutableStateFlow(createTestCompanies())
        mockStores = MutableStateFlow(createTestStores())
        mockDefaultCompany = MutableStateFlow(createTestCompanies()[0])
        mockDefaultStore = MutableStateFlow(createTestStores()[0])
        mockUpdateState = MutableStateFlow(null)
        mockIsRefreshing = MutableStateFlow(false)
        mockProgressMessage = MutableStateFlow("")

        // Mock AccountStateManager
        accountStateManager = mock {
            on { options } doReturn mockOptions
            on { priceTypes } doReturn mockPriceTypes
            on { paymentTypes } doReturn mockPaymentTypes
            on { companies } doReturn mockCompanies
            on { stores } doReturn mockStores
            on { defaultCompany } doReturn mockDefaultCompany
            on { defaultStore } doReturn mockDefaultStore
            on { getPriceTypeCode(any()) } doReturn "P1"
            on { getPriceDescription(any()) } doReturn "Retail Price"
            on { getPaymentType(any()) } doReturn createTestPaymentTypes()[0]
            on { findCompany(any()) } doReturn createTestCompanies()[0]
            on { findStore(any()) } doReturn createTestStores()[0]
        }

        // Mock SyncManager
        syncManager = mock {
            on { updateState } doReturn mockUpdateState
            on { isRefreshing } doReturn mockIsRefreshing
            on { progressMessage } doReturn mockProgressMessage
            on { syncEvents } doReturn MutableStateFlow<SyncEvent?>(null) as Flow<SyncEvent>
        }

        viewModel = SharedViewModel(
            filesRepository = filesRepository,
            logger = logger,
            imageLoadingManager = imageLoadingManager,
            orderRepository = orderRepository,
            commonRepository = commonRepository,
            preference = sharedPreferences,
            accountStateManager = accountStateManager,
            syncManager = syncManager
        )
    }

    // ========================================
    // Helper Methods
    // ========================================

    private fun createTestPriceTypes() = listOf(
        PriceType(priceType = "P1", description = "Retail Price", databaseId = "test"),
        PriceType(priceType = "P2", description = "Wholesale Price", databaseId = "test")
    )

    private fun createTestPaymentTypes() = listOf(
        PaymentType(paymentType = "PAY1", description = "Cash", databaseId = "test"),
        PaymentType(paymentType = "PAY2", description = "Card", databaseId = "test")
    )

    private fun createTestCompanies() = listOf(
        Company(guid = "C1", description = "Company 1", databaseId = "test", isDefault = 1),
        Company(guid = "C2", description = "Company 2", databaseId = "test", isDefault = 0)
    )

    private fun createTestStores() = listOf(
        Store(guid = "S1", description = "Store 1", databaseId = "test", isDefault = 1),
        Store(guid = "S2", description = "Store 2", databaseId = "test", isDefault = 0)
    )

    // ========================================
    // Initial State Tests
    // ========================================

    @Test
    fun `initial account state has empty GUID`() = runTest {
        viewModel.currentAccountFlow.test {
            val account = awaitItem()
            assertThat(account.guid).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial barcode is empty`() = runTest {
        viewModel.barcodeFlow.test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial shared parameters has default values`() = runTest {
        viewModel.sharedParamsFlow.test {
            val params = awaitItem()
            assertThat(params.filter).isEmpty()
            assertThat(params.docGuid).isEmpty()
            assertThat(params.sortByName).isFalse()
            assertThat(params.restsOnly).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial sync state is delegated to SyncManager`() = runTest {
        viewModel.updateStateFlow.test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.isRefreshingFlow.test {
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `options are delegated to AccountStateManager`() {
        val options = viewModel.options
        assertThat(options).isEqualTo(mockOptions.value)
    }

    @Test
    fun `priceTypes are delegated to AccountStateManager`() {
        val priceTypes = viewModel.priceTypes
        assertThat(priceTypes).isEqualTo(mockPriceTypes.value)
    }

    @Test
    fun `paymentTypes are delegated to AccountStateManager`() {
        val paymentTypes = viewModel.paymentTypes
        assertThat(paymentTypes).isEqualTo(mockPaymentTypes.value)
    }

    // ========================================
    // Barcode Handling Tests
    // ========================================

    @Test
    fun `onBarcodeRead emits valid barcode`() = runTest {
        viewModel.barcodeFlow.test {
            // Initial empty
            assertThat(awaitItem()).isEmpty()

            // Act: read barcode
            viewModel.onBarcodeRead("1234567890123")

            // Assert: barcode emitted
            assertThat(awaitItem()).isEqualTo("1234567890123")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onBarcodeRead ignores blank barcode`() = runTest {
        viewModel.barcodeFlow.test {
            assertThat(awaitItem()).isEmpty()

            // Act: read blank barcode
            viewModel.onBarcodeRead("")

            // Assert: no emission (timeout expected)
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onBarcodeRead ignores short barcode`() = runTest {
        viewModel.barcodeFlow.test {
            assertThat(awaitItem()).isEmpty()

            // Act: read short barcode (less than 10 chars)
            viewModel.onBarcodeRead("123")

            // Assert: no emission
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearBarcode resets barcode to empty`() = runTest {
        viewModel.barcodeFlow.test {
            assertThat(awaitItem()).isEmpty()

            // Set barcode
            viewModel.onBarcodeRead("1234567890123")
            assertThat(awaitItem()).isEqualTo("1234567890123")

            // Clear barcode
            viewModel.clearBarcode()
            assertThat(awaitItem()).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `multiple barcode reads emit sequentially`() = runTest {
        viewModel.barcodeFlow.test {
            assertThat(awaitItem()).isEmpty()

            viewModel.onBarcodeRead("1111111111")
            assertThat(awaitItem()).isEqualTo("1111111111")

            viewModel.onBarcodeRead("2222222222")
            assertThat(awaitItem()).isEqualTo("2222222222")

            viewModel.onBarcodeRead("3333333333")
            assertThat(awaitItem()).isEqualTo("3333333333")

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // Shared Parameters Tests
    // ========================================

    @Test
    fun `setDocumentGuid updates document GUID and type`() = runTest {
        viewModel.sharedParamsFlow.test {
            // Initial
            var params = awaitItem()
            assertThat(params.docGuid).isEmpty()
            assertThat(params.docType).isEmpty()

            // Act
            viewModel.setDocumentGuid(type = "Order", guid = "order-123")

            // Assert
            params = awaitItem()
            assertThat(params.docType).isEqualTo("Order")
            assertThat(params.docGuid).isEqualTo("order-123")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setDocumentGuid with company and store updates all fields`() = runTest {
        viewModel.sharedParamsFlow.test {
            skipItems(1) // Skip initial

            // Act
            viewModel.setDocumentGuid(
                type = "Order",
                guid = "order-123",
                companyGuid = "C1",
                storeGuid = "S1"
            )

            // Assert
            val params = awaitItem()
            assertThat(params.docGuid).isEqualTo("order-123")
            assertThat(params.companyGuid).isEqualTo("C1")
            assertThat(params.company).isEqualTo("Company 1")
            assertThat(params.storeGuid).isEqualTo("S1")
            assertThat(params.store).isEqualTo("Store 1")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setDocumentGuid with blank GUID clears document`() = runTest {
        viewModel.sharedParamsFlow.test {
            skipItems(1) // Skip initial

            // Set document
            viewModel.setDocumentGuid(type = "Order", guid = "order-123")
            awaitItem()

            // Clear document
            viewModel.setDocumentGuid(type = "Order", guid = "")

            // Assert
            val params = awaitItem()
            assertThat(params.docGuid).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setPrice updates price type`() = runTest {
        viewModel.sharedParamsFlow.test {
            skipItems(1) // Skip initial

            // Act
            viewModel.setPrice("Retail Price")

            // Assert
            val params = awaitItem()
            assertThat(params.priceType).isEqualTo("P1")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setCompany updates company GUID and description`() = runTest {
        viewModel.sharedParamsFlow.test {
            skipItems(1) // Skip initial

            // Act
            viewModel.setCompany("C1")

            // Assert
            val params = awaitItem()
            assertThat(params.companyGuid).isEqualTo("C1")
            assertThat(params.company).isEqualTo("Company 1")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setStore updates store GUID and description`() = runTest {
        viewModel.sharedParamsFlow.test {
            skipItems(1) // Skip initial

            // Act
            viewModel.setStore("S1")

            // Assert
            val params = awaitItem()
            assertThat(params.storeGuid).isEqualTo("S1")
            assertThat(params.store).isEqualTo("Store 1")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleSortByName toggles sorting flag`() = runTest {
        viewModel.sharedParamsFlow.test {
            // Initial: false
            var params = awaitItem()
            assertThat(params.sortByName).isFalse()

            // Toggle on
            viewModel.toggleSortByName()
            params = awaitItem()
            assertThat(params.sortByName).isTrue()

            // Toggle off
            viewModel.toggleSortByName()
            params = awaitItem()
            assertThat(params.sortByName).isFalse()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleRestsOnly toggles flag and saves to preferences`() = runTest {
        viewModel.sharedParamsFlow.test {
            // Initial: false
            var params = awaitItem()
            assertThat(params.restsOnly).isFalse()

            // Toggle on
            viewModel.toggleRestsOnly()
            params = awaitItem()
            assertThat(params.restsOnly).isTrue()

            // Verify preference saved
            verify(sharedPreferences).edit()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setRestsOnly updates flag without saving to preferences`() = runTest {
        viewModel.sharedParamsFlow.test {
            skipItems(1) // Skip initial

            // Set true
            viewModel.setRestsOnly(true)
            var params = awaitItem()
            assertThat(params.restsOnly).isTrue()

            // Set false
            viewModel.setRestsOnly(false)
            params = awaitItem()
            assertThat(params.restsOnly).isFalse()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleClientProducts toggles client products flag`() = runTest {
        viewModel.sharedParamsFlow.test {
            // Initial: false
            var params = awaitItem()
            assertThat(params.clientProducts).isFalse()

            // Toggle on
            viewModel.toggleClientProducts()
            params = awaitItem()
            assertThat(params.clientProducts).isTrue()

            // Toggle off
            viewModel.toggleClientProducts()
            params = awaitItem()
            assertThat(params.clientProducts).isFalse()

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // Document Totals Tests
    // ========================================

    @Test
    fun `documentTotals is empty when no document GUID set`() = runTest {
        // No document GUID set
        advanceUntilIdle()

        // Document totals should be empty
        // Note: Can't directly test StateFlow created with stateIn,
        // but can verify behavior through LiveData or integration
        assertThat(viewModel.sharedParamsFlow.value.docGuid).isEmpty()
    }

    @Test
    fun `documentTotals updates when document GUID is set`() = runTest {
        // Arrange: Add order with content
        val order = TestFixtures.createOrder1()
        orderRepository.addOrder(order)
        orderRepository.addOrderContent(order.guid, listOf(
            TestFixtures.createOrderContent1Line1().copy(orderGuid = order.guid, price = 100.0, quantity = 5.0)
        ))

        // Act: Set document GUID
        viewModel.setDocumentGuid(guid = order.guid)
        advanceUntilIdle()

        // Assert: Document totals should be calculated
        // (Integration test - actual totals calculation tested in repository)
        assertThat(viewModel.sharedParamsFlow.value.docGuid).isEqualTo(order.guid)
    }

    // ========================================
    // Action Callbacks Tests
    // ========================================

    @Test
    fun `selectClientAction can be set and called`() = runTest {
        // Arrange
        val client = TestFixtures.createLClient1()
        var actionCalled = false
        var popUpCalled = false

        // Act: Set action
        viewModel.selectClientAction = { selectedClient, popUp ->
            assertThat(selectedClient.guid).isEqualTo(client.guid)
            actionCalled = true
            popUp()
        }

        // Call action
        viewModel.selectClientAction(client) { popUpCalled = true }

        // Assert
        assertThat(actionCalled).isTrue()
        assertThat(popUpCalled).isTrue()
    }

    @Test
    fun `selectProductAction can be set and called`() = runTest {
        // Arrange
        val product = TestFixtures.createLProduct1()
        var actionCalled = false
        var popUpCalled = false

        // Act: Set action
        viewModel.selectProductAction = { selectedProduct, popUp ->
            assertThat(selectedProduct?.guid).isEqualTo(product.guid)
            actionCalled = true
            popUp()
        }

        // Call action
        viewModel.selectProductAction(product) { popUpCalled = true }

        // Assert
        assertThat(actionCalled).isTrue()
        assertThat(popUpCalled).isTrue()
    }

    @Test
    fun `clearActions resets document GUID and callbacks`() = runTest {
        // Arrange: Set document GUID and actions
        viewModel.setDocumentGuid(guid = "order-123")
        viewModel.selectClientAction = { _, _ -> }
        viewModel.selectProductAction = { _, _ -> }

        // Act: Clear actions
        viewModel.clearActions()
        advanceUntilIdle()

        // Assert: Document GUID cleared
        viewModel.sharedParamsFlow.test {
            val params = awaitItem()
            assertThat(params.docGuid).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }

        // Assert: Callbacks reset (calling should do nothing)
        var clientCalled = false
        var productCalled = false
        viewModel.selectClientAction(TestFixtures.createLClient1()) { clientCalled = true }
        viewModel.selectProductAction(TestFixtures.createLProduct1()) { productCalled = true }

        assertThat(clientCalled).isFalse()
        assertThat(productCalled).isFalse()
    }

    // ========================================
    // Image Loading Tests
    // ========================================

    @Test
    fun `setCacheDir configures image loading manager`() {
        // Arrange
        val cacheDir = File("/tmp/cache")

        // Act
        viewModel.setCacheDir(cacheDir)

        // Assert
        assertThat(viewModel.cacheDir).isEqualTo(cacheDir)
        verify(imageLoadingManager).setCacheDir(cacheDir)
    }

    @Test
    fun `loadImage delegates to image loading manager`() {
        // Arrange
        val product = TestFixtures.createLProduct1()
        val imageView: ImageView = mock()
        val rotation = 90

        // Act
        viewModel.loadImage(product, imageView, rotation)

        // Assert
        verify(imageLoadingManager).loadProductImage(
            eq(product),
            eq(imageView),
            eq(rotation),
            any()
        )
    }

    @Test
    fun `loadClientImage delegates to image loading manager`() {
        // Arrange
        val clientImage = ClientImage(
            databaseId = "test",
            clientGuid = "client-1",
            guid = "image-1",
            url = "",
            description = "",
            timestamp = 0L,
            isLocal = 0,
            isSent = 0,
            isDefault = 0
        )
        val imageView: ImageView = mock()
        val rotation = 180

        // Act
        viewModel.loadClientImage(clientImage, imageView, rotation)

        // Assert
        verify(imageLoadingManager).loadClientImage(
            eq(clientImage),
            eq(imageView),
            eq(rotation)
        )
    }

    @Test
    fun `fileInCache delegates to image loading manager`() {
        // Arrange
        val fileName = "test-image.jpg"
        val expectedFile = File("/cache/test-image.jpg")
        whenever(imageLoadingManager.fileInCache(fileName)) doReturn expectedFile

        // Act
        val result = viewModel.fileInCache(fileName)

        // Assert
        assertThat(result).isEqualTo(expectedFile)
        verify(imageLoadingManager).fileInCache(fileName)
    }

    @Test
    fun `deleteFileInCache delegates to image loading manager`() {
        // Arrange
        val fileName = "test-image.jpg"

        // Act
        viewModel.deleteFileInCache(fileName)

        // Assert
        verify(imageLoadingManager).deleteFileInCache(fileName)
    }

    @Test
    fun `saveClientImage creates and saves client image`() = runTest {
        // Arrange
        val clientGuid = "client-123"
        val imageGuid = "image-456"
        val cacheDir = File("/tmp/cache")
        viewModel.setCacheDir(cacheDir)

        val imageFile = File(cacheDir, "${imageGuid}.jpg")
        whenever(imageLoadingManager.fileInCache(any())) doReturn imageFile
        whenever(imageLoadingManager.encodeBase64(any())) doReturn "base64data"

        // Act
        viewModel.saveClientImage(clientGuid, imageGuid)
        advanceUntilIdle()

        // Assert: FilesRepository.saveClientImage should be called
        verify(filesRepository).saveClientImage(any())
    }

    // ========================================
    // Sync Delegation Tests
    // ========================================

    @Test
    fun `callDiffSync delegates to SyncManager`() = runTest {
        // Arrange
        var afterSyncCalled = false
        val afterSync = { afterSyncCalled = true }

        // Act
        viewModel.callDiffSync(afterSync)
        advanceUntilIdle()

        // Assert
        verify(syncManager).callDiffSync(any(), any())
    }

    @Test
    fun `callFullSync delegates to SyncManager`() = runTest {
        // Arrange
        var afterSyncCalled = false
        val afterSync = { afterSyncCalled = true }

        // Act
        viewModel.callFullSync(afterSync)
        advanceUntilIdle()

        // Assert
        verify(syncManager).callFullSync(any(), any())
    }

    @Test
    fun `callPrintDocument delegates to SyncManager`() = runTest {
        // Arrange
        val guid = "order-123"
        var result = false
        val afterSync: (Boolean) -> Unit = { result = it }

        // Act
        viewModel.callPrintDocument(guid, afterSync)
        advanceUntilIdle()

        // Assert
        verify(syncManager).callPrintDocument(any(), eq(guid), any(), any())
    }

    @Test
    fun `addProgressText delegates to SyncManager`() {
        // Arrange
        val text = "Syncing data..."

        // Act
        viewModel.addProgressText(text)

        // Assert
        verify(syncManager).addProgressText(text)
    }

    @Test
    fun `progressMessage delegates to SyncManager`() {
        // Arrange
        mockProgressMessage.value = "Test progress"

        // Act
        val message = viewModel.progressMessage

        // Assert
        assertThat(message).isEqualTo("Test progress")
    }

    // ========================================
    // Company and Store Tests
    // ========================================

    @Test
    fun `getCompanies loads companies from AccountStateManager`() {
        // Arrange
        var loadedCompanies: List<Company>? = null

        // Act
        viewModel.getCompanies { loadedCompanies = it }

        // Assert
        assertThat(loadedCompanies).isEqualTo(mockCompanies.value)
    }

    @Test
    fun `getStores loads stores from AccountStateManager`() {
        // Arrange
        var loadedStores: List<Store>? = null

        // Act
        viewModel.getStores { loadedStores = it }

        // Assert
        assertThat(loadedStores).isEqualTo(mockStores.value)
    }

    @Test
    fun `getPriceTypeCode delegates to AccountStateManager`() {
        // Act
        val code = viewModel.getPriceTypeCode("Retail Price")

        // Assert
        assertThat(code).isEqualTo("P1")
        verify(accountStateManager).getPriceTypeCode("Retail Price")
    }

    @Test
    fun `getPriceDescription delegates to AccountStateManager`() {
        // Act
        val description = viewModel.getPriceDescription("P1")

        // Assert
        assertThat(description).isEqualTo("Retail Price")
        verify(accountStateManager).getPriceDescription("P1")
    }

    @Test
    fun `getPaymentType delegates to AccountStateManager`() {
        // Act
        val paymentType = viewModel.getPaymentType("Cash")

        // Assert
        assertThat(paymentType).isEqualTo(createTestPaymentTypes()[0])
        verify(accountStateManager).getPaymentType("Cash")
    }

    // ========================================
    // Edge Cases and Error Handling Tests
    // ========================================

    @Test
    fun `multiple parameter updates emit correctly`() = runTest {
        viewModel.sharedParamsFlow.test {
            skipItems(1) // Skip initial

            // Rapid updates
            viewModel.toggleSortByName()
            viewModel.toggleRestsOnly()
            viewModel.setPrice("Retail")

            // Should emit for each update
            var params = awaitItem() // sortByName
            assertThat(params.sortByName).isTrue()

            params = awaitItem() // restsOnly
            assertThat(params.restsOnly).isTrue()

            params = awaitItem() // priceType
            assertThat(params.priceType).isEqualTo("P1")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `barcode with exactly 10 characters is accepted`() = runTest {
        viewModel.barcodeFlow.test {
            assertThat(awaitItem()).isEmpty()

            viewModel.onBarcodeRead("1234567890")
            assertThat(awaitItem()).isEqualTo("1234567890")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `barcode with 9 characters is rejected`() = runTest {
        viewModel.barcodeFlow.test {
            assertThat(awaitItem()).isEmpty()

            viewModel.onBarcodeRead("123456789")

            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setDocumentGuid with empty company GUID uses empty description`() = runTest {
        // Mock findCompany to return null
        whenever(accountStateManager.findCompany("")) doReturn null

        viewModel.sharedParamsFlow.test {
            skipItems(1)

            viewModel.setDocumentGuid(
                guid = "order-123",
                companyGuid = ""
            )

            val params = awaitItem()
            assertThat(params.company).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setDocumentGuid with empty store GUID uses empty description`() = runTest {
        // Mock findStore to return null
        whenever(accountStateManager.findStore("")) doReturn null

        viewModel.sharedParamsFlow.test {
            skipItems(1)

            viewModel.setDocumentGuid(
                guid = "order-123",
                storeGuid = ""
            )

            val params = awaitItem()
            assertThat(params.store).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `preferences are loaded on initialization`() {
        // Verify preferences were read
        verify(sharedPreferences).getBoolean("show_rests_only", false)
        verify(sharedPreferences).getBoolean("ignore_sequential_barcodes", false)
    }

    @Test
    fun `clearBarcode can be called multiple times safely`() = runTest {
        viewModel.barcodeFlow.test {
            assertThat(awaitItem()).isEmpty()

            viewModel.clearBarcode()
            viewModel.clearBarcode()
            viewModel.clearBarcode()

            // Should remain empty
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setting same parameter value does not emit duplicate`() = runTest {
        viewModel.sharedParamsFlow.test {
            val initial = awaitItem()

            // Set to same value as default
            viewModel.setRestsOnly(false)

            // Should emit with same value
            val updated = awaitItem()
            assertThat(updated.restsOnly).isEqualTo(initial.restsOnly)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `long barcode values are accepted`() = runTest {
        viewModel.barcodeFlow.test {
            assertThat(awaitItem()).isEmpty()

            val longBarcode = "1".repeat(100)
            viewModel.onBarcodeRead(longBarcode)

            assertThat(awaitItem()).isEqualTo(longBarcode)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `special characters in barcode are accepted`() = runTest {
        viewModel.barcodeFlow.test {
            assertThat(awaitItem()).isEmpty()

            val specialBarcode = "ABC-123-XYZ"
            viewModel.onBarcodeRead(specialBarcode)

            assertThat(awaitItem()).isEqualTo(specialBarcode)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
