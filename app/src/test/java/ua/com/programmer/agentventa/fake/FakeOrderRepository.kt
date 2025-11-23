package ua.com.programmer.agentventa.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ua.com.programmer.agentventa.data.local.entity.*
import ua.com.programmer.agentventa.data.local.entity.DocumentTotals
import ua.com.programmer.agentventa.domain.repository.OrderRepository
import java.util.*

/**
 * Fake implementation of OrderRepository for testing.
 * Provides in-memory storage with Flow support for reactive testing.
 */
open class FakeOrderRepository(
    private val currentAccountGuid: String = FakeUserAccountRepository.TEST_ACCOUNT_GUID
) : OrderRepository {

    private val orders = MutableStateFlow<List<Order>>(emptyList())
    private val orderContent = MutableStateFlow<Map<String, List<OrderContent>>>(emptyMap())
    private val clients = MutableStateFlow<List<Client>>(emptyList())
    private val companies = MutableStateFlow<List<Company>>(emptyList())
    private val stores = MutableStateFlow<List<Store>>(emptyList())
    private val priceTypes = MutableStateFlow<List<PriceType>>(emptyList())
    private val paymentTypes = MutableStateFlow<List<PaymentType>>(emptyList())

    override fun getDocument(guid: String): Flow<Order> = orders.map { list ->
        list.first { it.guid == guid }
    }

    override open suspend fun newDocument(): Order? {
        val now = Date()
        return Order(
            guid = UUID.randomUUID().toString(),
            databaseId = currentAccountGuid,
            date = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now),
            time = now.time,
            isSent = 0,
            isProcessed = 0
        )
    }

    override fun getDocuments(filter: String, listDate: Date?): Flow<List<Order>> = orders.map { list ->
        list.filter { order ->
            val matchesFilter = filter.isEmpty() ||
                    order.clientDescription?.contains(filter, ignoreCase = true) == true || order.number.toString().contains(filter, ignoreCase = true)

            val matchesDate = listDate == null || try {
                val orderDate = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(order.date)
                orderDate != null && isSameDay(orderDate, listDate)
            } catch (e: Exception) {
                false
            }

            matchesFilter && matchesDate
        }
    }

    override open suspend fun updateDocument(document: Order): Boolean {
        val currentList = orders.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.guid == document.guid }

        if (existingIndex >= 0) {
            currentList[existingIndex] = document
        } else {
            currentList.add(document)
        }

        orders.value = currentList
        return true
    }

    override suspend fun deleteDocument(document: Order): Boolean {
        val currentList = orders.value.toMutableList()
        val removed = currentList.removeIf { it.guid == document.guid }

        // Also remove associated content
        if (removed) {
            val contentMap = orderContent.value.toMutableMap()
            contentMap.remove(document.guid)
            orderContent.value = contentMap
        }

        orders.value = currentList
        return removed
    }

    override fun getDocumentListTotals(filter: String, listDate: Date?): Flow<List<DocumentTotals>> {
        return getDocuments(filter, listDate).map { orderList ->
            if (orderList.isEmpty()) {
                emptyList()
            } else {
                listOf(calculateTotals(orderList))
            }
        }
    }

    override suspend fun getOrder(guid: String): Order? {
        return orders.value.firstOrNull { it.guid == guid }
    }

    override suspend fun getClient(guid: String): Client? {
        return clients.value.firstOrNull { it.guid == guid }
    }

    override suspend fun getDocumentTotals(guid: String): DocumentTotals {
        val order = orders.value.firstOrNull { it.guid == guid }
        val content = orderContent.value[guid] ?: emptyList()

        return DocumentTotals(
            documents = if (order != null) 1 else 0,
            returns = if (order?.isReturn == 1) 1 else 0,
            weight = content.sumOf { it.weight },
            sum = content.sumOf { it.sum },
            discount = content.sumOf { it.discount },
            sumReturn = if (order?.isReturn == 1) content.sumOf { it.sum } else 0.0,
            quantity = content.sumOf { it.quantity }
        )
    }

    override fun watchDocumentTotals(guid: String): Flow<DocumentTotals> {
        return orderContent.map { contentMap ->
            val content = contentMap[guid] ?: emptyList()
            val order = orders.value.firstOrNull { it.guid == guid }

            DocumentTotals(
                documents = if (order != null) 1 else 0,
                returns = if (order?.isReturn == 1) 1 else 0,
                weight = content.sumOf { it.weight },
                sum = content.sumOf { it.sum },
                discount = content.sumOf { it.discount },
                sumReturn = if (order?.isReturn == 1) content.sumOf { it.sum } else 0.0,
                quantity = content.sumOf { it.quantity }
            )
        }
    }

    override fun getDocumentContent(guid: String): Flow<List<LOrderContent>> {
        return orderContent.map { contentMap ->
            (contentMap[guid] ?: emptyList()).map { it.toLOrderContent() }
        }
    }

    override suspend fun getContent(guid: String): List<LOrderContent> {
        return (orderContent.value[guid] ?: emptyList()).map { it.toLOrderContent() }
    }

    override suspend fun getContentLine(guid: String, productGuid: String): OrderContent {
        val content = orderContent.value[guid] ?: emptyList()
        return content.first { it.productGuid == productGuid }
    }

    override suspend fun updateContentLine(contentLine: OrderContent): Boolean {
        val contentMap = orderContent.value.toMutableMap()
        val orderGuid = contentLine.orderGuid
        val currentContent = contentMap[orderGuid]?.toMutableList() ?: mutableListOf()

        val existingIndex = currentContent.indexOfFirst {
            it.productGuid == contentLine.productGuid && it.orderGuid == contentLine.orderGuid
        }

        if (existingIndex >= 0) {
            currentContent[existingIndex] = contentLine
        } else {
            currentContent.add(contentLine)
        }

        contentMap[orderGuid] = currentContent
        orderContent.value = contentMap
        return true
    }

    override suspend fun copyPreviousContent(guid: String, clientGuid: String): Boolean {
        // Find the most recent order for this client
        val previousOrder = orders.value
            .filter { it.clientGuid == clientGuid && it.guid != guid }
            .maxByOrNull { it.date }
            ?: return false

        val previousContent = orderContent.value[previousOrder.guid] ?: return false

        // Copy content to new order
        val newContent = previousContent.map { it.copy(orderGuid = guid) }
        val contentMap = orderContent.value.toMutableMap()
        contentMap[guid] = newContent
        orderContent.value = contentMap

        return true
    }

    override suspend fun getPriceTypes(): List<PriceType> = priceTypes.value

    override suspend fun getPaymentTypes(): List<PaymentType> = paymentTypes.value

    override suspend fun getCompanies(): List<Company> = companies.value

    override suspend fun getStores(): List<Store> = stores.value

    override suspend fun updateLocation(document: Order): Boolean {
        return updateDocument(document)
    }

    override suspend fun setCompany(guid: String, company: Company) {
        val order = orders.value.firstOrNull { it.guid == guid } ?: return
        updateDocument(order.copy(
            companyGuid = company.guid,
            company = company.description
        ))
    }

    override suspend fun setStore(guid: String, store: Store) {
        val order = orders.value.firstOrNull { it.guid == guid } ?: return
        updateDocument(order.copy(
            storeGuid = store.guid,
            store = store.description
        ))
    }

    override suspend fun setClient(guid: String, client: Client) {
        val order = orders.value.firstOrNull { it.guid == guid } ?: return
        updateDocument(order.copy(
            clientGuid = client.guid,
            clientDescription = client.description
        ))
    }

    // Test helper methods

    fun addOrder(order: Order) {
        orders.value += order
    }

    fun addOrderContent(orderGuid: String, content: List<OrderContent>) {
        val contentMap = orderContent.value.toMutableMap()
        contentMap[orderGuid] = content
        orderContent.value = contentMap
    }

    fun addClient(client: Client) {
        clients.value += client
    }

    fun addCompany(company: Company) {
        companies.value += company
    }

    fun addStore(store: Store) {
        stores.value += store
    }

    fun setPriceTypes(types: List<PriceType>) {
        priceTypes.value = types
    }

    fun setPaymentTypes(types: List<PaymentType>) {
        paymentTypes.value = types
    }

    fun clearAll() {
        orders.value = emptyList()
        orderContent.value = emptyMap()
        clients.value = emptyList()
        companies.value = emptyList()
        stores.value = emptyList()
        priceTypes.value = emptyList()
        paymentTypes.value = emptyList()
    }

    private fun calculateTotals(orderList: List<Order>): DocumentTotals {
        return DocumentTotals(
            documents = orderList.count { it.isReturn == 0 },
            returns = orderList.count { it.isReturn == 1 },
            weight = orderList.sumOf { it.weight },
            sum = orderList.sumOf { it.price },
            discount = orderList.sumOf { it.discount },
            sumReturn = orderList.filter { it.isReturn == 1 }.sumOf { it.price },
            quantity = orderList.sumOf { it.quantity }
        )
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun OrderContent.toLOrderContent(): LOrderContent {
        return LOrderContent(
            orderGuid = this.orderGuid,
            productGuid = this.productGuid,
            code = "",
            description = "",
            unit = "",
            quantity = this.quantity,
            price = this.price,
            sum = this.sum,
            discount = this.discount,
            weight = this.weight,
        )
    }
}
