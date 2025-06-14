package ua.com.programmer.agentventa.documents.order

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.com.programmer.agentventa.dao.entity.Client
import ua.com.programmer.agentventa.dao.entity.LClient
import ua.com.programmer.agentventa.dao.entity.LProduct
import ua.com.programmer.agentventa.dao.entity.Order
import ua.com.programmer.agentventa.dao.entity.PaymentType
import ua.com.programmer.agentventa.dao.entity.setClient
import ua.com.programmer.agentventa.dao.entity.toUi
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

@HiltViewModel
class OrderViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val logger: Logger
): ViewModel() {

    private val _documentGuid = MutableLiveData("")
    private val currentDocument get() = document.value ?: Order(guid = "")

    // livedata is used by SharedModel to pass value to ProductListFragment
    private val _selectedPriceType = MutableLiveData<String>()
    val selectedPriceType get() = _selectedPriceType
    private var selectedPriceCode = ""

    private var ignoreBarcodes = false

    val navigateToPage = MutableLiveData<Int>()
    val saveResult = MutableLiveData<Boolean?>()

    val document = _documentGuid.switchMap {
        orderRepository.getDocument(it).asLiveData()
    }

    val currentContent = _documentGuid.switchMap {
        orderRepository.getDocumentContent(it).asLiveData()
    }

    fun setSharedParameters(ignoreBarcodeReads: Boolean) {
        ignoreBarcodes = ignoreBarcodeReads
    }

    private fun updateDocument(updated: Order) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                orderRepository.updateDocument(updated)
            }
        }
    }

    /**
     * Document GUID initialized on activity creation, it initiates
     * database reading an document data.
     */
    fun setCurrentDocument(id: String?) {
        if (id.isNullOrEmpty()) {
            initNewDocument()
        } else {
            _documentGuid.value = id
        }
    }

    private fun initNewDocument() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val document = orderRepository.newDocument()
                if (document != null) {
                    withContext(Dispatchers.Main) {
                        _documentGuid.value = document.guid
                    }
                } else {
                    logger.e("OrderVM", "init new: failed to create new document")
                }
            }
        }
    }

    fun setClient(clientGuid: String?) {
        if (clientGuid.isNullOrEmpty() || currentDocument.guid.isEmpty()) return
        if ((currentDocument.clientGuid ?: "").isNotEmpty()) return
        viewModelScope.launch {
            orderRepository.getClient(clientGuid)?.let {client ->
                orderRepository.getOrder(currentDocument.guid)?.let {order ->
                    order.setClient(client.toUi())
                    orderRepository.updateDocument(order)
                }
            }
        }
    }

    fun onClientClick(client: LClient, popUp: () -> Unit) {
        val orderGuid = currentDocument.guid
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

    // TODO: check price, discount
    fun onProductClick(product: LProduct?, popUp: () -> Unit) {

        if (currentDocument.isProcessed == 1) return popUp()

        if (product == null) return
        val orderGuid = currentDocument.guid

        viewModelScope.launch {

            val contentLine = orderRepository.getContentLine(orderGuid, product.guid)

            val quantity = product.quantity.roundToInt(1000)
            val price = product.price.roundToInt(100)
            val sumTotal = price * quantity
            val sum = BigDecimal(sumTotal)
                .divide(BigDecimal(100000))
                .setScale(2, RoundingMode.HALF_UP)
                .toDouble()
            //Log.d("Checkbox", "$sumTotal --> ${sumTotal.toDouble() / 100000} --> $sum")

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
                updateDocument(currentDocument.copy(
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
        if (currentDocument.priceType != code) {
            updateDocument(currentDocument.copy(priceType = code))
        }
    }

    fun onPaymentTypeSelected(type: PaymentType) {
        if (currentDocument.paymentType != type.paymentType) {
            updateDocument(currentDocument.copy(paymentType = type.paymentType, isFiscal = type.isFiscal))
        }
    }

    fun onIsReturnClick(isChecked: Boolean) {
        val isReturn = if (isChecked) 1 else 0
        if (currentDocument.isReturn != isReturn) {
            updateDocument(currentDocument.copy(isReturn = isReturn))
        }
    }

    fun onEditNotes(notes: String) {
        updateDocument(currentDocument.copy(notes = notes))
    }

    fun saveDocument() {
        val updated = currentDocument.copy(
            isProcessed = 1,
            timeSaved = System.currentTimeMillis() / 1000
        )
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val saved = orderRepository.updateDocument(updated)
                withContext(Dispatchers.Main) {
                    saveResult.value = saved
                }
            }
        }
    }

    /**
     * Prepares the document to be saved by updating its totals and then
     * executing the provided continuation function if document is fiscal.
     * Non-fiscal documents are saved immediately.
     *
     * @param continueFiscal A function to be executed after the document is prepared.
     */
    fun prepareToSave(continueFiscal: (orderGuid: String) -> Unit) {
        viewModelScope.launch {
            val orderGuid = currentDocument.guid
            val totals = orderRepository.getDocumentTotals(orderGuid)
            updateDocument(currentDocument.copy(
                price = totals.sum.round(2),
                quantity = totals.quantity.round(3),
                weight = totals.weight.round(3),
                discountValue = totals.discount.round(2),
            ))
            if (isFiscal()) {
                withContext(Dispatchers.Main) {
                    continueFiscal(orderGuid)
                }
            }else {
                saveDocument()
            }
        }
    }

    fun deleteDocument() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                orderRepository.deleteDocument(currentDocument)
            }
        }
    }

    fun enableEdit() {
        updateDocument(currentDocument.copy(isProcessed = 0, isSent = 0))
    }

    fun isNotEditable() = currentDocument.isProcessed > 0

    fun isFiscal() = currentDocument.isFiscal == 1

    fun getGuid() = currentDocument.guid

    fun getCompanyGuid() = currentDocument.companyGuid

    fun canPrint() = currentDocument.isSent > 0 && currentDocument.guid.isNotEmpty()

    fun notReadyToProcess(): Boolean {
        if (currentDocument.clientGuid.isNullOrEmpty()) return true
        if (currentDocument.isFiscal == 1) {
            if (currentDocument.price == 0.0) return true
            if (currentDocument.quantity == 0.0) return true
            if (currentDocument.paymentType.isEmpty()) return true
        }
        return false
    }

    fun updateLocation(onComplete: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                orderRepository.updateLocation(currentDocument)
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    fun copyPrevious(onComplete: (Boolean) -> Unit) {
        val clientGuid = currentDocument.clientGuid ?: ""
        if (clientGuid.isEmpty()) return onComplete(false)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val result = orderRepository.copyPreviousContent(currentDocument.guid, clientGuid)
                if (result) {
                    val totals = orderRepository.getDocumentTotals(currentDocument.guid)
                    updateDocument(currentDocument.copy(
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
        updateDocument(currentDocument.copy(deliveryDate = date.localFormatted()))
    }

    fun onBarcodeRead(barcode: String, onFail: () -> Unit) {
        viewModelScope.launch {
            val product = productRepository.getProductByBarcode(barcode, currentDocument.guid, currentDocument.priceType)
            if (product != null) {
                if (product.quantity > 0 && ignoreBarcodes) {
                    onProductClick(product) {}
                } else {
                    val updated = product.copy(
                        quantity = product.quantity + 1,
                    )
                    onProductClick(updated) {}
                }
            } else {
                logger.w("OrderVM", "product not found; barcode=$barcode")
                withContext(Dispatchers.Main) {
                    onFail()
                }
            }
            navigateToPage.value = 1
        }
    }

    fun onDestroy() {
        _documentGuid.value = ""
        navigateToPage.value = -1
        saveResult.value = null
    }

}