package ua.com.programmer.agentventa.dao.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ua.com.programmer.agentventa.dao.LocationDao
import ua.com.programmer.agentventa.dao.OrderDao
import ua.com.programmer.agentventa.dao.UserAccountDao
import ua.com.programmer.agentventa.dao.entity.Client
import ua.com.programmer.agentventa.dao.entity.DocumentTotals
import ua.com.programmer.agentventa.dao.entity.LOrderContent
import ua.com.programmer.agentventa.dao.entity.LocationHistory
import ua.com.programmer.agentventa.dao.entity.Order
import ua.com.programmer.agentventa.dao.entity.OrderContent
import ua.com.programmer.agentventa.dao.entity.PaymentType
import ua.com.programmer.agentventa.dao.entity.PriceType
import ua.com.programmer.agentventa.dao.entity.interval
import ua.com.programmer.agentventa.dao.entity.updateDistance
import ua.com.programmer.agentventa.extensions.asFilter
import ua.com.programmer.agentventa.extensions.beginOfDay
import ua.com.programmer.agentventa.extensions.endOfDay
import ua.com.programmer.agentventa.repository.OrderRepository
import ua.com.programmer.agentventa.settings.UserOptionsBuilder
import ua.com.programmer.agentventa.utility.Utils
import java.util.Date
import javax.inject.Inject

class OrderRepositoryImpl @Inject constructor(
    private val orderDao: OrderDao,
    private val userAccountDao: UserAccountDao,
    private val locationDao: LocationDao
): OrderRepository {

    private val utils = Utils()

    override fun getDocument(guid: String): Flow<Order> {
        return orderDao.getDocument(guid).map { order ->
            order ?: Order(guid = "") }
    }

    override suspend fun getOrder(guid: String): Order? {
        return orderDao.getOrder(guid)
    }

    override suspend fun getClient(guid: String): Client? {
        return orderDao.getClient(guid)
    }

    override suspend fun newDocument(): Order? {

        val userAccount = userAccountDao.getCurrent() ?: return null
        val options = UserOptionsBuilder.build(userAccount)
        val newNumber = (orderDao.getLastDocumentNumber() ?: 0) + 1
        val time = utils.currentTime()
        val dbGuid = userAccount.guid
        val priceTypes = getPriceTypes()
        val paymentTypes = getPaymentTypes()

        val defaultPriceType = if (priceTypes.isEmpty()) {
            ""
        } else {
            priceTypes.first().priceType
        }
        val defaultPaymentType = if (paymentTypes.isEmpty()) {
            PaymentType()
        } else {
            val payment = paymentTypes.find { it.isDefault == 1 } ?: paymentTypes.first()
            payment
        }

        val client = if (options.defaultClient.isNotBlank()) {
            getClient(options.defaultClient)
        } else {
            null
        }

        val document = Order(
            databaseId = dbGuid,
            number = newNumber,
            guid = java.util.UUID.randomUUID().toString(),
            time = time,
            date = utils.dateLocal(time),
            priceType = defaultPriceType,
            paymentType = defaultPaymentType.paymentType,
            isFiscal = defaultPaymentType.isFiscal,
            clientGuid = options.defaultClient,
            clientCode2 = client?.code2 ?: "",
            clientDescription = client?.description ?: "",
        )

        val lastLocation = locationDao.getLastLocation() ?: LocationHistory()
        if (lastLocation.interval(time) < 1800) {
            document.latitude = lastLocation.latitude
            document.longitude = lastLocation.longitude
            document.locationTime = lastLocation.time / 1000
        }

        // after document is saved, it has id
        orderDao.save(document)
        // so need to load new data
        return orderDao.getOrder(document.guid)
    }

    override fun getDocuments(filter: String, listDate: Date?): Flow<List<Order>> {
        if (listDate == null) return orderDao.getDocumentsWithFilter(filter.asFilter())
        val startTime = listDate.beginOfDay()
        val endTime = listDate.endOfDay()
        return orderDao.getDocumentsWithFilter(filter.asFilter(), startTime, endTime)
    }

    override fun getDocumentListTotals(filter: String, listDate: Date?): Flow<List<DocumentTotals>> {
        if (listDate == null) return orderDao.getDocumentsTotals(filter.asFilter())
        val startTime = listDate.beginOfDay()
        val endTime = listDate.endOfDay()
        return orderDao.getDocumentsTotals(filter.asFilter(), startTime, endTime)
    }

    override suspend fun updateDocument(document: Order): Boolean {
        return orderDao.update(document) > 0
    }

    override fun getDocumentContent(guid: String): Flow<List<LOrderContent>> {
        return orderDao.getOrderContent(guid).map { content ->
            content ?: listOf() }
    }

    override suspend fun getContent(guid: String): List<LOrderContent> {
        return orderDao.getContent(guid) ?: listOf()
    }

    override suspend fun getDocumentTotals(guid: String): DocumentTotals {
        return orderDao.getDocumentTotals(guid) ?: DocumentTotals()
    }

    override fun watchDocumentTotals(guid: String): Flow<DocumentTotals> {
        return orderDao.watchDocumentTotals(guid).map { documentTotals ->
            documentTotals ?: DocumentTotals() }
    }

    override suspend fun getContentLine(guid: String, productGuid: String): OrderContent {
        return orderDao.getContentLine(guid, productGuid) ?: OrderContent(
            orderGuid = guid,
            productGuid = productGuid,
        )
    }

    override suspend fun updateContentLine(contentLine: OrderContent): Boolean {
        if (contentLine.quantity == 0.0) return orderDao.deleteContentLine(contentLine.orderGuid, contentLine.productGuid) > 0
        return orderDao.insertContentLine(contentLine) > 0
    }

    override suspend fun copyPreviousContent(guid: String, clientGuid: String): Boolean {
        val todayTime = utils.dateBeginOfToday()
        val previousOrder = orderDao.getOrder(clientGuid, todayTime) ?: return false
        val periodBegin = utils.dateBeginOfDay(previousOrder.time)
        val periodEnd = periodBegin + 86400000
        val previousContent = orderDao.getContentInPeriod(periodBegin, periodEnd, clientGuid)
        if (previousContent.isEmpty()) return false
        orderDao.clearContent(guid)
        previousContent.forEach { contentLine ->
            val newContent = contentLine.copy(id = 0, orderGuid = guid)
            orderDao.insertContentLine(newContent)
        }
        return true
    }

    override suspend fun getPriceTypes(): List<PriceType> {
        return orderDao.getPriceTypes() ?: listOf()
    }

    override suspend fun getPaymentTypes(): List<PaymentType> {
        val list = orderDao.getPaymentTypes() ?: listOf()
        if (list.isNotEmpty()) return list
        return listOf(
            PaymentType(paymentType = "CASH", description = "Готівка"),
            PaymentType(paymentType = "CARD", description = "Картка", isFiscal = 1),
            PaymentType(paymentType = "BANK", description = "Банк"),
            PaymentType(paymentType = "CREDIT", description = "Кредит", isDefault = 1),
        )
    }

    override suspend fun updateLocation(document: Order): Boolean {
        locationDao.getLastLocation()?.let { lastLocation ->
            document.latitude = lastLocation.latitude
            document.longitude = lastLocation.longitude
            document.locationTime = lastLocation.time / 1000
        }
        val clientGuid = document.clientGuid ?: ""
        if (clientGuid.isNotEmpty()) {
            locationDao.getClientLocation(clientGuid)?.let {
                document.updateDistance(it.latitude, it.longitude)
            }
        }
        return orderDao.update(document) > 0
    }

    override suspend fun deleteDocument(document: Order): Boolean {
        return orderDao.delete(document) > 0
    }
}