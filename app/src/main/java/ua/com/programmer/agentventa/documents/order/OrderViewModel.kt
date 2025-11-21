package ua.com.programmer.agentventa.documents.order

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
import ua.com.programmer.agentventa.dao.entity.Client
import ua.com.programmer.agentventa.dao.entity.LClient
import ua.com.programmer.agentventa.dao.entity.LProduct
import ua.com.programmer.agentventa.dao.entity.Order
import ua.com.programmer.agentventa.dao.entity.LOrderContent
import ua.com.programmer.agentventa.dao.entity.PaymentType
import ua.com.programmer.agentventa.dao.entity.setClient
import ua.com.programmer.agentventa.dao.entity.toUi
import ua.com.programmer.agentventa.documents.common.DocumentViewModel
import ua.com.programmer.agentventa.domain.result.DomainException
import ua.com.programmer.agentventa.domain.result.Result
import ua.com.programmer.agentventa.domain.usecase.order.EnableOrderEditUseCase
import ua.com.programmer.agentventa.domain.usecase.order.SaveOrderUseCase
import ua.com.programmer.agentventa.domain.usecase.order.ValidateOrderUseCase
import ua.com.programmer.agentventa.extensions.localFormatted
import ua.com.programmer.agentventa.extensions.round
import ua.com.programmer.agentventa.extensions.roundToInt
import ua.com.programmer.agentventa.logger.Logger
import ua.com.programmer.agentventa.repository.OrderRepository
import ua.com.programmer.agentventa.repository.ProductRepository
import java.math.BigDecimal
import java.math.RoundingMode
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
    logger: Logger
) : DocumentViewModel<Order>(
    repository = orderRepository,
    logger = logger,
    logTag = "OrderVM",
    emptyDocument = { Order(guid = "") }
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
    val currentContent: androidx.lifecycle.MutableLiveData<List<LOrderContent>> = androidx.lifecycle.MutableLiveData(emptyList())

    init {
        viewModelScope.launch {
            _currentContentFlow.collect { content ->
                currentContent.postValue(content)
            }
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

    fun setClient(clientGuid: String?) {
        if (clientGuid.isNullOrEmpty() || order.guid.isEmpty()) return
        if ((order.clientGuid ?: "").isNotEmpty()) return
        viewModelScope.launch {
            orderRepository.getClient(clientGuid)?.let { client ->
                orderRepository.getOrder(order.guid)?.let { currentOrder ->
                    currentOrder.setClient(client.toUi())
                    orderRepository.updateDocument(currentOrder)
                }
            }
        }
    }

    fun onClientClick(client: LClient, popUp: () -> Unit) {
        val orderGuid = order.guid
        viewModelScope.launch {
            val clientData = Client(
                guid = client.guid,
                description = client.description,
            )
            orderRepository.setClient(orderGuid, clientData)
            withContext(Dispatchers.Main) {
                popUp()
            }
        }
    }

    fun onProductClick(product: LProduct?, popUp: () -> Unit) {
        if (order.isProcessed == 1) return popUp()
        if (product == null) return

        val orderGuid = order.guid

        viewModelScope.launch {
            val contentLine = orderRepository.getContentLine(orderGuid, product.guid)

            val quantity = product.quantity.roundToInt(1000)
            val price = product.price.roundToInt(100)
            val sumTotal = price * quantity
            val sum = BigDecimal(sumTotal)
                .divide(BigDecimal(100000))
                .setScale(2, RoundingMode.HALF_UP)
                .toDouble()

            val updated = contentLine.copy(
                unitCode = product.unit,
                price = product.price,
                quantity = product.quantity,
                sum = sum,
                weight = product.weight * product.quantity,
                isPacked = if (product.isPacked) 1 else 0,
                isDemand = if (product.isDemand) 1 else 0,
            )

            if (orderRepository.updateContentLine(updated)) {
                val totals = orderRepository.getDocumentTotals(orderGuid)
                updateDocument(order.copy(
                    price = totals.sum.round(2),
                    quantity = totals.quantity.round(3),
                    weight = totals.weight.round(3),
                    discountValue = totals.discount.round(2),
                ))

                withContext(Dispatchers.Main) {
                    popUp()
                }
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
                withContext(Dispatchers.Main) {
                    continueFiscal(orderGuid)
                }
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
            withContext(Dispatchers.IO) {
                orderRepository.updateLocation(order)
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    fun copyPrevious(onComplete: (Boolean) -> Unit) {
        val clientGuid = order.clientGuid ?: ""
        if (clientGuid.isEmpty()) return onComplete(false)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val result = orderRepository.copyPreviousContent(order.guid, clientGuid)
                if (result) {
                    val totals = orderRepository.getDocumentTotals(order.guid)
                    updateDocument(order.copy(
                        price = totals.sum,
                        quantity = totals.quantity,
                        weight = totals.weight,
                        discountValue = totals.discount,
                    ))
                }
                withContext(Dispatchers.Main) {
                    onComplete(result)
                }
            }
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
                withContext(Dispatchers.Main) {
                    onFail()
                }
            }
            // Set navigation page
            _navigateToPage.value = 1
        }
    }
}
