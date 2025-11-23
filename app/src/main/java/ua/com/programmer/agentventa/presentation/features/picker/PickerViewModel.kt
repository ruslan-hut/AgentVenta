package ua.com.programmer.agentventa.presentation.features.picker

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.data.local.entity.LPrice
import ua.com.programmer.agentventa.data.local.entity.LProduct
import ua.com.programmer.agentventa.extensions.round
import ua.com.programmer.agentventa.domain.repository.ProductRepository
import ua.com.programmer.agentventa.utility.Constants
import javax.inject.Inject

@HiltViewModel
class PickerViewModel @Inject constructor(
    private val productRepository: ProductRepository
): ViewModel() {

    private val _product = MutableLiveData<LProduct>()
    val product get() = _product

    private val _priceList = MutableLiveData<List<LPrice>>()
    val priceList get() = _priceList

    fun setProductParameters(guid: String, orderGuid: String, priceType: String) {
        viewModelScope.launch {
            productRepository.getProduct(guid, orderGuid, priceType).collect { prod ->
                _product.value = prod

                productRepository.fetchProductPrices(guid, priceType).collect { list ->
                    _priceList.value = list

                    if (prod.packageOnly && prod.packageValue > 0 && prod.unitType != Constants.UNIT_PACKAGE) {
                        switchPackageUnit()
                    }
                }
            }
        }
    }

    private fun updatePriceList(k: Double) {
        val current = priceList.value ?: return
        val updated = current.map {
            it.copy(
                price = it.price * k,
                basePrice = it.basePrice * k,
            )
        }
        _priceList.value = updated
    }

    fun switchPackageUnit() {
        val current = product.value ?: return
        if (current.packageValue == 0.0) return
        val updated = when (current.unitType) {
            // switch from default to package
            Constants.UNIT_DEFAULT -> {
                updatePriceList(current.packageValue)
                current.copy(
                    unitType = Constants.UNIT_PACKAGE,
                    quantity = (current.quantity / current.packageValue).round(0),
                    rest = (current.rest / current.packageValue).round(0),
                    price = current.price * current.packageValue,
                    basePrice = current.basePrice * current.packageValue,
                )
            }
            // switch from kilo to package
            Constants.UNIT_WEIGHT -> {
                val kiloPackage = current.packageValue * current.weight
                if (kiloPackage == 0.0) return
                updatePriceList(kiloPackage)
                current.copy(
                    unitType = Constants.UNIT_PACKAGE,
                    quantity = (current.quantity / kiloPackage).round(0),
                    rest = (current.rest / kiloPackage).round(0),
                    price = current.price * kiloPackage,
                    basePrice = current.basePrice * kiloPackage,
                )
            }
            // switch from package to default
            else -> {
                updatePriceList(1/current.packageValue)
                current.copy(
                    unitType = Constants.UNIT_DEFAULT,
                    quantity = current.quantity * current.packageValue,
                    rest = current.rest * current.packageValue,
                    price = current.price / current.packageValue,
                    basePrice = current.basePrice / current.packageValue,
                )
            }
        }
        _product.value = updated
    }

    fun switchWeightUnit() {
        val current = product.value ?: return
        if (current.weight == 0.0) return
        val updated = when (current.unitType) {
            // switch from default to kilo
            Constants.UNIT_DEFAULT -> {
                updatePriceList(1/current.weight)
                current.copy(
                    unitType = Constants.UNIT_WEIGHT,
                    quantity = current.quantity * current.weight,
                    rest = current.rest * current.weight,
                    price = current.price / current.weight,
                    basePrice = current.basePrice / current.weight,
                )
            }
            // switch from package to default
            Constants.UNIT_PACKAGE -> {
                val weightPackage = current.packageValue * current.weight
                if (weightPackage == 0.0) return
                updatePriceList(1/weightPackage)
                current.copy(
                    unitType = Constants.UNIT_WEIGHT,
                    quantity = current.quantity * weightPackage,
                    rest = current.rest * weightPackage,
                    price = current.price / weightPackage,
                    basePrice = current.basePrice / weightPackage,
                )
            }
            // switch from kilo to default
            else -> {
                updatePriceList(current.weight)
                current.copy(
                    unitType = Constants.UNIT_DEFAULT,
                    quantity = current.quantity / current.weight,
                    rest = current.rest / current.weight,
                    price = current.price * current.weight,
                    basePrice = current.basePrice * current.weight,
                )
            }
        }
        _product.value = updated
    }
}