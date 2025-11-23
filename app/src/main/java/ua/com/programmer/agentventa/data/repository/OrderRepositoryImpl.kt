package ua.com.programmer.agentventa.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import ua.com.programmer.agentventa.data.local.dao.LocationDao
import ua.com.programmer.agentventa.data.local.dao.OrderDao
import ua.com.programmer.agentventa.data.local.dao.UserAccountDao
import ua.com.programmer.agentventa.data.local.entity.Client
import ua.com.programmer.agentventa.data.local.entity.Company
import ua.com.programmer.agentventa.data.local.entity.DocumentTotals
import ua.com.programmer.agentventa.data.local.entity.LOrderContent
import ua.com.programmer.agentventa.data.local.entity.LocationHistory
import ua.com.programmer.agentventa.data.local.entity.Order
import ua.com.programmer.agentventa.data.local.entity.OrderContent
import ua.com.programmer.agentventa.data.local.entity.PaymentType
import ua.com.programmer.agentventa.data.local.entity.PriceType
import ua.com.programmer.agentventa.data.local.entity.Store
import ua.com.programmer.agentventa.data.local.entity.UserAccount
import ua.com.programmer.agentventa.data.local.entity.interval
import ua.com.programmer.agentventa.data.local.entity.updateDistance
import ua.com.programmer.agentventa.extensions.asFilter
import ua.com.programmer.agentventa.extensions.beginOfDay
import ua.com.programmer.agentventa.extensions.endOfDay
import ua.com.programmer.agentventa.domain.repository.OrderRepository
import ua.com.programmer.agentventa.domain.repository.UserAccountRepository
import ua.com.programmer.agentventa.presentation.features.settings.UserOptions
import ua.com.programmer.agentventa.presentation.features.settings.UserOptionsBuilder
import ua.com.programmer.agentventa.utility.UtilsInterface
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class OrderRepositoryImpl @Inject constructor(
    private val orderDao: OrderDao,
    private val userAccountDao: UserAccountDao,
    private val userAccountRepository: UserAccountRepository,
    private val locationDao: LocationDao,
    private val utils: UtilsInterface
): OrderRepository {

    private var userAccount = UserAccount.buildEmpty()
    private lateinit var options: UserOptions
    private var companies = emptyList<Company>()
    private var stores = emptyList<Store>()
    private var priceTypes = emptyList<PriceType>()
    private var paymentTypes = emptyList<PaymentType>()

    private suspend fun getCurrentDbGuid(): String = userAccountRepository.currentAccountGuid.first()

    private suspend fun initDefaults() {
        val currentAccount = userAccountDao.getCurrent() ?: UserAccount.buildEmpty()
        if (userAccount != currentAccount) {
            options = UserOptionsBuilder.build(currentAccount)
            companies = getCompanies()
            stores = getStores()
            priceTypes = getPriceTypes()
            paymentTypes = getPaymentTypes()
            userAccount = currentAccount
        }
    }

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

        initDefaults()

        val currentDbGuid = getCurrentDbGuid()
        val newNumber = (orderDao.getLastDocumentNumber(currentDbGuid) ?: 0) + 1
        val time = utils.currentTime()
        val dbGuid = userAccount.guid

        val company = companies.find { it.isDefault == 1 } ?: Company()
        val store = stores.find { it.isDefault == 1 } ?: Store()

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
            companyGuid = company.guid,
            company = company.description,
            storeGuid = store.guid,
            store = store.description,
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
        return userAccountRepository.currentAccountGuid.flatMapLatest { currentDbGuid ->
            if (listDate == null) {
                orderDao.getDocumentsWithFilter(currentDbGuid, filter.asFilter())
            } else {
                val startTime = listDate.beginOfDay()
                val endTime = listDate.endOfDay()
                orderDao.getDocumentsWithFilter(currentDbGuid, filter.asFilter(), startTime, endTime)
            }
        }
    }

    override fun getDocumentListTotals(filter: String, listDate: Date?): Flow<List<DocumentTotals>> {
        return userAccountRepository.currentAccountGuid.flatMapLatest { currentDbGuid ->
            if (listDate == null) {
                orderDao.getDocumentsTotals(currentDbGuid, filter.asFilter())
            } else {
                val startTime = listDate.beginOfDay()
                val endTime = listDate.endOfDay()
                orderDao.getDocumentsTotals(currentDbGuid, filter.asFilter(), startTime, endTime)
            }
        }
    }

    override suspend fun updateDocument(document: Order): Boolean {
        return orderDao.update(document) > 0
    }

    override suspend fun setClient(guid: String, client: Client) {
        return orderDao.setClient(guid, client.guid, client.description)
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

    override suspend fun getCompanies(): List<Company> {
        if (companies.isNotEmpty()) return companies
        return orderDao.getCompanies() ?: listOf()
    }

    override suspend fun getStores(): List<Store> {
        if (stores.isNotEmpty()) return stores
        return orderDao.getStores() ?: listOf()
    }

    override suspend fun updateLocation(document: Order): Boolean {
        val currentDbGuid = getCurrentDbGuid()
        locationDao.getLastLocation()?.let { lastLocation ->
            document.latitude = lastLocation.latitude
            document.longitude = lastLocation.longitude
            document.locationTime = lastLocation.time / 1000
        }
        val clientGuid = document.clientGuid ?: ""
        if (clientGuid.isNotEmpty()) {
            locationDao.getClientLocation(currentDbGuid, clientGuid)?.let {
                document.updateDistance(it.latitude, it.longitude)
            }
        }
        return orderDao.update(document) > 0
    }

    override suspend fun setCompany(
        guid: String,
        company: Company,
    ) {
        orderDao.setCompany(guid, company.guid, company.description)
    }

    override suspend fun setStore(
        guid: String,
        store: Store,
    ) {
        orderDao.setStore(guid, store.guid, store.description)
    }

    override suspend fun deleteDocument(document: Order): Boolean {
        return orderDao.delete(document) > 0
    }
}