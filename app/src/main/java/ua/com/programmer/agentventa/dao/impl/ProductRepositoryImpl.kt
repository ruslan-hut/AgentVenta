package ua.com.programmer.agentventa.dao.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ua.com.programmer.agentventa.shared.SharedParameters
import ua.com.programmer.agentventa.dao.ProductDao
import ua.com.programmer.agentventa.dao.entity.LPrice
import ua.com.programmer.agentventa.dao.entity.LProduct
import ua.com.programmer.agentventa.extensions.asFilter
import ua.com.programmer.agentventa.extensions.asInt
import ua.com.programmer.agentventa.repository.ProductRepository
import javax.inject.Inject

class ProductRepositoryImpl @Inject constructor(
    private val productDao: ProductDao
) : ProductRepository {

    override fun getProduct(guid: String): Flow<LProduct> {
//        return productDao.getProduct(guid).map {
//            it?.toUi() ?: return@map LProduct()
//        }
        return productDao.getProductOrderContent(guid, "", "").map {
            it ?: return@map LProduct()
        }
    }

    override fun getProduct(guid: String, orderGuid: String, priceType: String): Flow<LProduct> {
        return productDao.getProductOrderContent(guid, orderGuid, priceType).map {
            it ?: return@map LProduct()
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
        return productDao.fetchProductPrices(guid, currentPriceType).map {
            it ?: return@map emptyList()
        }
    }

    override suspend fun getProductByBarcode(barcode: String, orderGuid: String, priceType: String
    ): LProduct? {
        return productDao.getProductByBarcode("$barcode%", orderGuid, priceType)
    }

}