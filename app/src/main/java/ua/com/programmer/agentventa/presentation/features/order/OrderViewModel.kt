package ua.com.programmer.agentventa.presentation.features.order

import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineDispatcher
import ua.com.programmer.agentventa.data.local.entity.LClient
import ua.com.programmer.agentventa.data.local.entity.LProduct
import ua.com.programmer.agentventa.data.local.entity.Order
import ua.com.programmer.agentventa.data.local.entity.LOrderContent
import ua.com.programmer.agentventa.data.local.entity.PaymentType
import ua.com.programmer.agentventa.data.local.entity.PreviousOrderContent
import ua.com.programmer.agentventa.data.local.entity.setClient
import ua.com.programmer.agentventa.data.local.entity.toUi
import ua.com.programmer.agentventa.di.CoroutineModule.IoDispatcher
import ua.com.programmer.agentventa.presentation.common.document.DocumentViewModel
import ua.com.programmer.agentventa.domain.result.DomainException
import ua.com.programmer.agentventa.domain.result.Result
import ua.com.programmer.agentventa.domain.usecase.order.EnableOrderEditUseCase
import ua.com.programmer.agentventa.domain.usecase.order.GenerateOrderPrintUseCase
import ua.com.programmer.agentventa.domain.usecase.order.SaveOrderUseCase
import ua.com.programmer.agentventa.domain.usecase.order.ValidateOrderUseCase
import ua.com.programmer.agentventa.domain.usecase.order.toPrintData
import ua.com.programmer.agentventa.infrastructure.printer.WebhookPrintService
import ua.com.programmer.agentventa.extensions.localFormatted
import ua.com.programmer.agentventa.extensions.calculateLineSum
import ua.com.programmer.agentventa.extensions.round
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import ua.com.programmer.agentventa.domain.repository.OrderRepository
import ua.com.programmer.agentventa.domain.repository.ProductRepository
import ua.com.programmer.agentventa.domain.usecase.order.GetProductDiscountUseCase
import ua.com.programmer.agentventa.presentation.common.viewmodel.AccountStateManager
import java.util.Date
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OrderViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val validateOrderUseCase: ValidateOrderUseCase,
    private val saveOrderUseCase: SaveOrderUseCase,
    private val enableOrderEditUseCase: EnableOrderEditUseCase,
    private val generateOrderPrintUseCase: GenerateOrderPrintUseCase,
    private val getProductDiscountUseCase: GetProductDiscountUseCase,
    private val accountStateManager: AccountStateManager,
    private val webhookPrintService: WebhookPrintService,
    logger: Logger,
    @IoDispatcher ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DocumentViewModel<Order>(
    repository = orderRepository,
    logger = logger,
    logTag = "OrderVM",
    emptyDocument = { Order(guid = "") },
    ioDispatcher = ioDispatcher
) {

    private val order get() = currentDocument

    // StateFlow for selected price type
    private val _selectedPriceType = MutableStateFlow("")
    val selectedPriceTypeFlow: StateFlow<String> = _selectedPriceType.asStateFlow()
    val selectedPriceType: androidx.lifecycle.LiveData<String> = _selectedPriceType.asLiveData()
    private var selectedPriceCode = ""

    private var ignoreBarcodes = false

    // Navigation LiveData for backward compatibility
    private val _navigateToPage = MutableStateFlow(-1)
    val navigateToPage: androidx.lifecycle.LiveData<Int> = _navigateToPage.asLiveData()

    fun setNavigatePage(page: Int) {
        _navigateToPage.value = page
    }

    // Order content as StateFlow
    private val _currentContentFlow: StateFlow<List<LOrderContent>> = _documentGuid
        .flatMapLatest { guid ->
            if (guid.isEmpty()) flowOf(emptyList())
            else orderRepository.getDocumentContent(guid)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )
    val currentContentFlow: StateFlow<List<LOrderContent>> get() = _currentContentFlow
    val currentContent = _currentContentFlow.asLiveData()

    // Previous order content
    private val _previousContent = MutableStateFlow<List<PreviousOrderContent>>(emptyList())
    val previousContent: StateFlow<List<PreviousOrderContent>> = _previousContent.asStateFlow()

    fun loadPreviousContent() {
        val clientGuid = order.clientGuid ?: return
        if (clientGuid.isEmpty()) return
        viewModelScope.launch {
            val content = withContext(ioDispatcher) {
                orderRepository.getPreviousOrderContent(clientGuid)
            }
            _previousContent.value = content
        }
    }

    fun copySelectedProducts(items: List<PreviousOrderContent>, onComplete: (Boolean) -> Unit) {
        if (items.isEmpty()) return onComplete(false)
        viewModelScope.launch {
            withContext(ioDispatcher) {
                items.forEach { item ->
                    val contentLine = orderRepository.getContentLine(order.guid, item.productGuid)
                    val updated = contentLine.copy(
                        unitCode = item.unit,
                        price = item.price,
                        quantity = item.quantity,
                        sum = calculateLineSum(item.price, item.quantity)
                    )
                    orderRepository.updateContentLine(updated)
                }
                refreshOrderTotals()
            }
            onComplete(true)
        }
    }

    override fun getDocumentGuid(document: Order): String = document.guid

    override fun markAsProcessed(document: Order): Order =
        document.copy(isProcessed = 1, timeSaved = System.currentTimeMillis() / 1000)

    override fun enableEdit() {
        viewModelScope.launch {
            enableOrderEditUseCase(order)
        }
    }

    override fun isNotEditable(): Boolean = order.isProcessed > 0

    override fun onEditNotes(notes: String) {
        updateDocument(order.copy(notes = notes))
    }

    fun setSharedParameters(ignoreBarcodeReads: Boolean) {
        ignoreBarcodes = ignoreBarcodeReads
    }

    fun setClient(clientGuid: String?, setClientPrice: Boolean = true) {
        if (clientGuid.isNullOrEmpty() || order.guid.isEmpty()) return
        if ((order.clientGuid ?: "").isNotEmpty()) return
        viewModelScope.launch {
            val client = orderRepository.getClient(clientGuid) ?: return@launch
            val currentOrder = orderRepository.getOrder(order.guid) ?: return@launch
            val oldPriceType = currentOrder.priceType
            currentOrder.setClient(client.toUi(), setClientPrice)
            orderRepository.updateDocument(currentOrder)
            if (currentOrder.priceType != oldPriceType) {
                recalculateContent(currentOrder)
            }
        }
    }

    fun onClientClick(client: LClient, setClientPrice: Boolean, popUp: () -> Unit) {
        val orderGuid = order.guid
        viewModelScope.launch {
            val currentOrder = orderRepository.getOrder(orderGuid) ?: return@launch
            val oldPriceType = currentOrder.priceType
            currentOrder.setClient(client, setClientPrice)
            orderRepository.updateDocument(currentOrder)
            if (currentOrder.priceType != oldPriceType) {
                recalculateContent(currentOrder)
            }
            popUp()
        }
    }

    private suspend fun recalculateContent(currentOrder: Order) {
        orderRepository.recalculateContentPrices(currentOrder.guid, currentOrder.priceType)
        refreshOrderTotals()
    }

    private suspend fun refreshOrderTotals() {
        val totals = orderRepository.getDocumentTotals(order.guid)
        updateDocument(order.copy(
            price = totals.sum.round(2),
            quantity = totals.quantity.round(3),
            weight = totals.weight.round(3),
            discountValue = totals.discount.round(2),
        ))
    }

    private suspend fun calculateLineDiscount(
        price: Double,
        quantity: Double,
        productGuid: String,
        groupGuid: String,
    ): Double {
        val options = accountStateManager.options.value
        if (!options.complexDiscounts) return 0.0
        val clientGuid = order.clientGuid ?: return 0.0
        if (clientGuid.isEmpty()) return 0.0
        val dbGuid = order.databaseId
        val params = GetProductDiscountUseCase.Params(dbGuid, clientGuid, productGuid, groupGuid)
        val result = getProductDiscountUseCase(params)
        val discountPercent = (result as? Result.Success)?.data ?: return 0.0
        if (discountPercent == 0.0) return 0.0
        return calculateLineSum(price, quantity) * discountPercent / 100.0
    }

    fun onProductClick(product: LProduct?, popUp: () -> Unit) {
        if (order.isProcessed == 1) return popUp()
        if (product == null) return

        val orderGuid = order.guid

        viewModelScope.launch {
            val contentLine = orderRepository.getContentLine(orderGuid, product.guid)
            val lineSum = calculateLineSum(product.price, product.quantity)
            val discount = calculateLineDiscount(
                product.price, product.quantity, product.guid, product.groupGuid
            )

            val updated = contentLine.copy(
                unitCode = product.unit,
                price = product.price,
                quantity = product.quantity,
                sum = lineSum - discount,
                discount = discount,
                weight = product.weight * product.quantity,
                isPacked = if (product.isPacked) 1 else 0,
                isDemand = if (product.isDemand) 1 else 0,
            )

            if (orderRepository.updateContentLine(updated)) {
                refreshOrderTotals()
                popUp()
            }
        }
    }

    fun onPriceTypeSelected(code: String, description: String) {
        _selectedPriceType.value = description
        selectedPriceCode = code
        if (order.priceType != code) {
            updateDocument(order.copy(priceType = code))
        }
    }

    fun onPaymentTypeSelected(type: PaymentType) {
        if (order.paymentType != type.paymentType) {
            updateDocument(order.copy(paymentType = type.paymentType, isFiscal = type.isFiscal))
        }
    }

    fun onIsReturnClick(isChecked: Boolean) {
        val isReturn = if (isChecked) 1 else 0
        if (order.isReturn != isReturn) {
            updateDocument(order.copy(isReturn = isReturn))
        }
    }

    fun saveDocument() {
        val updated = markAsProcessed(order)
        updateDocumentWithResult(updated)
    }

    fun prepareToSave(continueFiscal: (orderGuid: String) -> Unit) {
        viewModelScope.launch {
            val orderGuid = order.guid
            val totals = orderRepository.getDocumentTotals(orderGuid)
            updateDocument(order.copy(
                price = totals.sum.round(2),
                quantity = totals.quantity.round(3),
                weight = totals.weight.round(3),
                discountValue = totals.discount.round(2),
            ))
            if (isFiscal()) {
                continueFiscal(orderGuid)
            } else {
                saveDocument()
            }
        }
    }

    fun isFiscal() = order.isFiscal == 1

    fun getCompanyGuid() = order.companyGuid

    fun canPrint() = order.isSent > 0 && order.guid.isNotEmpty()

    /**
     * Check if order is not ready to process using validation use case.
     * Returns validation error message or null if valid.
     */
    suspend fun validateOrder(): String? {
        return when (val result = validateOrderUseCase(order)) {
            is Result.Success -> null
            is Result.Error -> {
                when (val ex = result.exception) {
                    is DomainException.ValidationError -> ex.message
                    else -> ex.message
                }
            }
        }
    }

    /**
     * Legacy method for backward compatibility.
     * Prefer using validateOrder() for detailed error messages.
     */
    fun notReadyToProcess(): Boolean {
        if (order.clientGuid.isNullOrEmpty()) return true
        if (order.isFiscal == 1) {
            if (order.price == 0.0) return true
            if (order.quantity == 0.0) return true
            if (order.paymentType.isEmpty()) return true
        }
        return false
    }

    fun updateLocation(onComplete: () -> Unit) {
        viewModelScope.launch {
            withContext(ioDispatcher) { orderRepository.updateLocation(order) }
            onComplete()
        }
    }

    fun copyPrevious(onComplete: (Boolean) -> Unit) {
        val clientGuid = order.clientGuid ?: ""
        if (clientGuid.isEmpty()) return onComplete(false)
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                val copied = orderRepository.copyPreviousContent(order.guid, clientGuid)
                if (copied) {
                    refreshOrderTotals()
                }
                copied
            }
            onComplete(result)
        }
    }

    fun setDeliveryDate(date: Date) {
        updateDocument(order.copy(deliveryDate = date.localFormatted()))
    }

    fun onBarcodeRead(barcode: String, onFail: () -> Unit) {
        viewModelScope.launch {
            val product = productRepository.getProductByBarcode(barcode, order.guid, order.priceType)
            if (product != null) {
                if (product.quantity > 0 && ignoreBarcodes) {
                    onProductClick(product) {}
                } else {
                    val updated = product.copy(quantity = product.quantity + 1)
                    onProductClick(updated) {}
                }
            } else {
                logger.w(logTag, "product not found; barcode=$barcode")
                onFail()
            }
            // Set navigation page
            _navigateToPage.value = 1
        }
    }

    // ==================== Print Methods ====================

    /**
     * Check if webhook printing is enabled and configured.
     */
    fun isWebhookPrintEnabled(): Boolean {
        return webhookPrintService.isEnabled() && webhookPrintService.isConfigured()
    }

    /**
     * Generate text file for Bluetooth printing.
     *
     * @param cacheDir Directory to save the generated file
     * @param deviceId Device identifier for footer (last 6 chars will be shown)
     * @param onResult Callback with file name on success or null on failure
     */
    fun generatePrintText(
        cacheDir: java.io.File,
        deviceId: String = "",
        onResult: (String?) -> Unit
    ) {
        viewModelScope.launch {
            val params = GenerateOrderPrintUseCase.Params(
                orderGuid = order.guid,
                outputDir = cacheDir,
                companyName = order.company,
                deviceId = deviceId
            )

            when (val result = generateOrderPrintUseCase(params)) {
                is Result.Success -> onResult(result.data.name)
                is Result.Error -> {
                    logger.e(logTag, "Print generation failed: ${result.exception.message}")
                    onResult(null)
                }
            }
        }
    }

    /**
     * Send order data to webhook for printing.
     *
     * @param onResult Callback with success status and message
     */
    fun sendToWebhook(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val content = orderRepository.getContent(order.guid)
            val printData = order.toPrintData(content)

            val result = webhookPrintService.sendPrintData(printData)

            result.fold(
                onSuccess = { message ->
                    onResult(true, message)
                },
                onFailure = { error ->
                    logger.e(logTag, "Webhook print failed: ${error.message}")
                    onResult(false, error.message ?: "Unknown error")
                }
            )
        }
    }
}
