package ua.com.programmer.agentventa.repository

import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.dao.entity.Client
import ua.com.programmer.agentventa.dao.entity.Company
import ua.com.programmer.agentventa.dao.entity.DocumentTotals
import ua.com.programmer.agentventa.dao.entity.LOrderContent
import ua.com.programmer.agentventa.dao.entity.Order
import ua.com.programmer.agentventa.dao.entity.OrderContent
import ua.com.programmer.agentventa.dao.entity.PaymentType
import ua.com.programmer.agentventa.dao.entity.PriceType
import ua.com.programmer.agentventa.dao.entity.Store
import java.util.Date

interface OrderRepository: DocumentRepository<Order> {

    override fun getDocument(guid: String): Flow<Order>
    override suspend fun newDocument(): Order?
    override fun getDocuments(filter: String, listDate: Date?): Flow<List<Order>>
    override suspend fun updateDocument(document: Order): Boolean

    suspend fun getOrder(guid: String): Order?
    suspend fun getClient(guid: String): Client?
    suspend fun getDocumentTotals(guid: String): DocumentTotals
    fun watchDocumentTotals(guid: String): Flow<DocumentTotals>
    fun getDocumentContent(guid: String): Flow<List<LOrderContent>>
    suspend fun getContent(guid: String): List<LOrderContent>
    suspend fun getContentLine(guid: String, productGuid: String): OrderContent
    suspend fun updateContentLine(contentLine: OrderContent): Boolean
    suspend fun copyPreviousContent(guid: String, clientGuid: String): Boolean
    suspend fun getPriceTypes(): List<PriceType>
    suspend fun getPaymentTypes(): List<PaymentType>
    suspend fun getCompanies(): List<Company>
    suspend fun getStores(): List<Store>
    suspend fun updateLocation(document: Order): Boolean
}