package ua.com.programmer.agentventa.catalogs.product

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.dao.entity.LProduct
import ua.com.programmer.agentventa.repository.ProductRepository
import javax.inject.Inject

@HiltViewModel
class ProductImageViewModel @Inject constructor(
    private val productRepository: ProductRepository
): ViewModel()  {

    private val _product = MutableLiveData<LProduct>()
    val product get() = _product

    fun setProductParameters(guid: String) {
        viewModelScope.launch {
            productRepository.getProduct(guid).collect {
                _product.value = it
            }
        }
    }

}