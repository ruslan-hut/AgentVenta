package ua.com.programmer.agentventa.domain.repository

import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.data.local.entity.LPrice
import ua.com.programmer.agentventa.presentation.common.viewmodel.SharedParameters
import ua.com.programmer.agentventa.data.local.entity.LProduct

interface ProductRepository {
    fun getProduct(guid: String): Flow<LProduct>
    fun getProduct(guid: String, orderGuid: String, priceType: String): Flow<LProduct>
    fun getProducts(parameters: SharedParameters): Flow<List<LProduct>>
    fun fetchProductPrices(guid: String, currentPriceType: String): Flow<List<LPrice>>
    suspend fun getProductByBarcode(barcode: String, orderGuid: String, priceType: String): LProduct?
}