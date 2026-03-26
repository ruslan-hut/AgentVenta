package ua.com.programmer.agentventa.presentation.features.picker

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.data.local.dao.DiscountDao
import ua.com.programmer.agentventa.data.local.entity.LPrice
import ua.com.programmer.agentventa.data.local.entity.LProduct
import ua.com.programmer.agentventa.extensions.DiscountResolver
import ua.com.programmer.agentventa.extensions.round
import ua.com.programmer.agentventa.domain.repository.OrderRepository
import ua.com.programmer.agentventa.domain.repository.ProductRepository
import ua.com.programmer.agentventa.presentation.common.viewmodel.AccountStateManager
import ua.com.programmer.agentventa.utility.Constants
import javax.inject.Inject

@HiltViewModel
class PickerViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val orderRepository: OrderRepository,
    private val discountDao: DiscountDao,
    private val accountStateManager: AccountStateManager,
): ViewModel() {

    private val _product = MutableLiveData<LProduct>()
    val product get() = _product

    private val _priceList = MutableLiveData<List<LPrice>>()
    val priceList get() = _priceList

    private val _discountPercent = MutableLiveData(0.0)
    val discountPercent get() = _discountPercent

    fun setProductParameters(guid: String, orderGuid: String, priceType: String) {
        viewModelScope.launch {
            resolveDiscount(guid, orderGuid)

            productRepository.getProduct(guid, orderGuid, priceType).collect { prod ->
                // If the order line already has a discount, reverse-calculate percent from it
                val existingDiscount = prod.orderDiscount ?: 0.0
                if (existingDiscount != 0.0 && prod.quantity != 0.0 && prod.price != 0.0) {
                    val lineSum = prod.price * prod.quantity
                    _discountPercent.value = existingDiscount / lineSum * 100.0
                }

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

    private suspend fun resolveDiscount(productGuid: String, orderGuid: String) {
        val options = accountStateManager.options.value
        if (!options.complexDiscounts) return
        val order = orderRepository.getOrder(orderGuid) ?: return
        val clientGuid = order.clientGuid ?: return
        if (clientGuid.isEmpty()) return
        val dbGuid = accountStateManager.currentAccount.value.guid
        if (dbGuid.isEmpty()) return
        _discountPercent.value = DiscountResolver.resolve(discountDao, dbGuid, clientGuid, productGuid)
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