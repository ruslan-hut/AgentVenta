package ua.com.programmer.agentventa.catalogs.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import ua.com.programmer.agentventa.dao.entity.LPrice
import ua.com.programmer.agentventa.dao.entity.LProduct
import ua.com.programmer.agentventa.repository.ProductRepository
import javax.inject.Inject

data class ProductParams(
    val guid: String = "",
    val orderGuid: String = "",
    val priceType: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProductViewModel @Inject constructor(
    private val productRepository: ProductRepository
): ViewModel() {

    private val _params = MutableStateFlow(ProductParams())

    // Product as StateFlow
    private val _productFlow: StateFlow<LProduct?> = _params
        .flatMapLatest { params ->
            if (params.guid.isEmpty()) flowOf(null)
            else productRepository.getProduct(params.guid, params.orderGuid, params.priceType)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    val productFlow: StateFlow<LProduct?> = _productFlow
    val product = _productFlow.asLiveData()

    // Price list as StateFlow
    private val _priceListFlow: StateFlow<List<LPrice>> = _params
        .flatMapLatest { params ->
            if (params.guid.isEmpty()) flowOf(emptyList())
            else productRepository.fetchProductPrices(params.guid, params.priceType)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    val priceListFlow: StateFlow<List<LPrice>> = _priceListFlow
    val priceList = _priceListFlow.asLiveData()

    fun setProductParameters(guid: String, orderGuid: String, priceType: String) {
        _params.value = ProductParams(guid, orderGuid, priceType)
    }
}
