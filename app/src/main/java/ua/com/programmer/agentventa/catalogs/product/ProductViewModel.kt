package ua.com.programmer.agentventa.catalogs.product

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.dao.entity.LPrice
import ua.com.programmer.agentventa.dao.entity.LProduct
import ua.com.programmer.agentventa.repository.ProductRepository
import javax.inject.Inject

@HiltViewModel
class ProductViewModel @Inject constructor(
    private val productRepository: ProductRepository
): ViewModel()  {

    private val _product = MutableLiveData<LProduct>()
    val product get() = _product

    private val _priceList = MutableLiveData<List<LPrice>>()
    val priceList get() = _priceList

    fun setProductParameters(guid: String, orderGuid: String, priceType: String) {
        viewModelScope.launch {
            productRepository.getProduct(guid, orderGuid, priceType).collect {
                _product.value = it
            }
        }
        viewModelScope.launch {
            productRepository.fetchProductPrices(guid, priceType).collect {
                _priceList.value = it
            }
        }
    }

}