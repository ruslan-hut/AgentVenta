package ua.com.programmer.agentventa.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ua.com.programmer.agentventa.data.local.entity.LPrice
import ua.com.programmer.agentventa.data.local.entity.LProduct
import ua.com.programmer.agentventa.domain.repository.ProductRepository
import ua.com.programmer.agentventa.presentation.common.viewmodel.SharedParameters

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
            val price = prices.value[guid]?.firstOrNull { it.priceType == priceType }
            product.copy(
                price = price?.price ?: product.price,
                priceType = priceType
            )
        }
    }

    override fun getProducts(parameters: SharedParameters): Flow<List<LProduct>> = products.map { list ->
        list.filter { product ->
            val matchesFilter = parameters.filter.isEmpty() || product.description.contains(parameters.filter, ignoreCase = true) || product.code.contains(parameters.filter, ignoreCase = true)
            matchesFilter
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
                price.copy(isCurrent = price.priceType == currentPriceType)
            } ?: emptyList()
        }
    }

    override suspend fun getProductByBarcode(barcode: String, orderGuid: String, priceType: String): LProduct? {
        return products.value.firstOrNull { product ->
            product.code == barcode
        }?.let { product ->
            // Apply price type to the product
            val price = prices.value[product.guid]?.firstOrNull { it.priceType == priceType }
            product.copy(
                price = price?.price ?: product.price,
                priceType = priceType
            )
        }
    }

    // Test helper methods

    fun addProduct(product: LProduct) {
        products.value += product
    }

    fun addProducts(vararg productList: LProduct) {
        products.value += productList
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
                //databaseId = FakeUserAccountRepository.TEST_ACCOUNT_GUID,
                code = barcode,
                description = description,
                unit = "шт",
                price = price,
                rest = rest,
                //barcode = barcode,
                //group = "",
                isGroup = false,
                weight = 0.0,
            )
        }
    }
}
