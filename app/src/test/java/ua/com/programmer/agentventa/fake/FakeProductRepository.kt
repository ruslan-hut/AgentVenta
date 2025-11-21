package ua.com.programmer.agentventa.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ua.com.programmer.agentventa.dao.entity.LPrice
import ua.com.programmer.agentventa.dao.entity.LProduct
import ua.com.programmer.agentventa.repository.ProductRepository
import ua.com.programmer.agentventa.shared.SharedParameters

/**
 * Fake implementation of ProductRepository for testing.
 * Provides in-memory storage for product catalog data.
 */
class FakeProductRepository(
    private val currentAccountGuid: String = FakeUserAccountRepository.TEST_ACCOUNT_GUID
) : ProductRepository {

    private val products = MutableStateFlow<List<LProduct>>(emptyList())
    private val prices = MutableStateFlow<Map<String, List<LPrice>>>(emptyMap())

    override fun getProduct(guid: String): Flow<LProduct> = products.map { list ->
        list.first { it.guid == guid }
    }

    override fun getProduct(guid: String, orderGuid: String, priceType: String): Flow<LProduct> = products.map { list ->
        list.first { it.guid == guid }.let { product ->
            // Apply price type to the product
            val price = prices.value[guid]?.firstOrNull { it.type == priceType }
            product.copy(
                price = price?.price ?: product.price,
                priceType = priceType
            )
        }
    }

    override fun getProducts(parameters: SharedParameters): Flow<List<LProduct>> = products.map { list ->
        list.filter { product ->
            val matchesGroup = parameters.groupGuid.isEmpty() || product.group == parameters.groupGuid
            val matchesFilter = parameters.filter.isEmpty() ||
                product.description?.contains(parameters.filter, ignoreCase = true) == true ||
                product.code?.contains(parameters.filter, ignoreCase = true) == true
            val matchesStore = parameters.storeGuid.isEmpty() || product.store == parameters.storeGuid
            val matchesCompany = parameters.companyGuid.isEmpty() || product.company == parameters.companyGuid
            val matchesRest = !parameters.restsOnly || (product.rest ?: 0.0) > 0.0

            matchesGroup && matchesFilter && matchesStore && matchesCompany && matchesRest
        }.let { filtered ->
            if (parameters.sortByName) {
                filtered.sortedBy { it.description }
            } else {
                filtered
            }
        }
    }

    override fun fetchProductPrices(guid: String, currentPriceType: String): Flow<List<LPrice>> {
        return prices.map { priceMap ->
            priceMap[guid]?.map { price ->
                price.copy(isCurrent = price.type == currentPriceType)
            } ?: emptyList()
        }
    }

    override suspend fun getProductByBarcode(barcode: String, orderGuid: String, priceType: String): LProduct? {
        return products.value.firstOrNull { product ->
            product.barcode == barcode || product.barcode2 == barcode || product.barcode3 == barcode
        }?.let { product ->
            // Apply price type to the product
            val price = prices.value[product.guid]?.firstOrNull { it.type == priceType }
            product.copy(
                price = price?.price ?: product.price,
                priceType = priceType
            )
        }
    }

    // Test helper methods

    fun addProduct(product: LProduct) {
        products.value = products.value + product
    }

    fun addProducts(vararg productList: LProduct) {
        products.value = products.value + productList
    }

    fun addProductPrices(productGuid: String, priceList: List<LPrice>) {
        val currentPrices = prices.value.toMutableMap()
        currentPrices[productGuid] = priceList
        prices.value = currentPrices
    }

    fun clearAll() {
        products.value = emptyList()
        prices.value = emptyMap()
    }

    companion object {
        /**
         * Creates a default test product with common fields populated
         */
        fun createTestProduct(
            guid: String = "test-product-1",
            code: String = "PROD001",
            description: String = "Test Product",
            price: Double = 100.0,
            rest: Double = 50.0,
            barcode: String = "1234567890"
        ): LProduct {
            return LProduct(
                guid = guid,
                db_guid = FakeUserAccountRepository.TEST_ACCOUNT_GUID,
                code = code,
                description = description,
                unit = "шт",
                price = price,
                rest = rest,
                barcode = barcode,
                group = "",
                isGroup = 0,
                weight = 0.0,
                volume = 0.0
            )
        }
    }
}
