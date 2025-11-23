package ua.com.programmer.agentventa.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import ua.com.programmer.agentventa.presentation.common.viewmodel.SharedParameters
import ua.com.programmer.agentventa.data.local.dao.ProductDao
import ua.com.programmer.agentventa.data.local.entity.LPrice
import ua.com.programmer.agentventa.data.local.entity.LProduct
import ua.com.programmer.agentventa.extensions.asFilter
import ua.com.programmer.agentventa.extensions.asInt
import ua.com.programmer.agentventa.domain.repository.ProductRepository
import ua.com.programmer.agentventa.domain.repository.UserAccountRepository
import javax.inject.Inject

class ProductRepositoryImpl @Inject constructor(
    private val productDao: ProductDao,
    private val userAccountRepository: UserAccountRepository
) : ProductRepository {

    private suspend fun getCurrentDbGuid(): String = userAccountRepository.currentAccountGuid.first()

    override fun getProduct(guid: String): Flow<LProduct> {
        return userAccountRepository.currentAccountGuid.flatMapLatest { currentDbGuid ->
            productDao.getProductOrderContent(currentDbGuid, guid, "", "").map {
                it ?: LProduct()
            }
        }
    }

    override fun getProduct(guid: String, orderGuid: String, priceType: String): Flow<LProduct> {
        return userAccountRepository.currentAccountGuid.flatMapLatest { currentDbGuid ->
            productDao.getProductOrderContent(currentDbGuid, guid, orderGuid, priceType).map {
                it ?: LProduct()
            }
        }
    }

    override fun getProducts(parameters: SharedParameters): Flow<List<LProduct>> {
        return if (parameters.storeGuid.isNotBlank()) {
            productDao.getProductsWithRests(
                filter = parameters.filter.asFilter(),
                group = parameters.groupGuid,
                order = parameters.docGuid,
                restOnly = parameters.restsOnly.asInt(),
                sorting = if (parameters.sortByName) "product.description_lc" else "product.sorting",
                type = parameters.priceType,
                dbGuid = parameters.currentAccount,
                company = parameters.companyGuid,
                store = parameters.storeGuid,
            )
        }else{
            productDao.getProducts(
                filter = parameters.filter.asFilter(),
                group = parameters.groupGuid,
                order = parameters.docGuid,
                restOnly = parameters.restsOnly.asInt(),
                sorting = if (parameters.sortByName) "product.description_lc" else "product.sorting",
                type = parameters.priceType,
                dbGuid = parameters.currentAccount,
            )
        }
    }

    override fun fetchProductPrices(guid: String, currentPriceType: String): Flow<List<LPrice>> {
        return userAccountRepository.currentAccountGuid.flatMapLatest { currentDbGuid ->
            productDao.fetchProductPrices(currentDbGuid, guid, currentPriceType).map {
                it ?: emptyList()
            }
        }
    }

    override suspend fun getProductByBarcode(barcode: String, orderGuid: String, priceType: String
    ): LProduct? {
        val currentDbGuid = getCurrentDbGuid()
        return productDao.getProductByBarcode(currentDbGuid, "$barcode%", orderGuid, priceType)
    }

}