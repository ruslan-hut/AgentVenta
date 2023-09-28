package ua.com.programmer.agentventa.dao.impl

import ua.com.programmer.agentventa.dao.DataExchangeDao
import ua.com.programmer.agentventa.dao.entity.Client
import ua.com.programmer.agentventa.dao.entity.ClientImage
import ua.com.programmer.agentventa.dao.entity.ClientLocation
import ua.com.programmer.agentventa.dao.entity.Debt
import ua.com.programmer.agentventa.dao.entity.LOrderContent
import ua.com.programmer.agentventa.dao.entity.Order
import ua.com.programmer.agentventa.dao.entity.PaymentType
import ua.com.programmer.agentventa.dao.entity.PriceType
import ua.com.programmer.agentventa.dao.entity.Product
import ua.com.programmer.agentventa.dao.entity.ProductImage
import ua.com.programmer.agentventa.dao.entity.ProductPrice
import ua.com.programmer.agentventa.dao.entity.isValid
import ua.com.programmer.agentventa.http.SendResult
import ua.com.programmer.agentventa.logger.Logger
import ua.com.programmer.agentventa.repository.DataExchangeRepository
import ua.com.programmer.agentventa.utility.Constants
import ua.com.programmer.agentventa.utility.XMap
import javax.inject.Inject

class DataExchangeRepositoryImpl @Inject constructor(
    private val dataExchangeDao: DataExchangeDao,
    private val logger: Logger
) : DataExchangeRepository {

    private val logTag = "DataExRepo"

    override suspend fun saveData(data: List<XMap>) {
        separator(data)
    }

    private suspend fun separator(data: List<XMap>) {
        val type = data.first().getValueId()
        val listToSave = data.filter { it.getValueId() == type }
        try {
            saveFilteredData(listToSave)
        } catch (e: Exception) {
            logger.e(logTag, "separator: ${e.message}")
        }
        if (listToSave.size < data.size) separator(data.filter { it.getValueId() != type })
    }

    private suspend fun saveFilteredData(data: List<XMap>) {
        when (data.first().getValueId()) {
            Constants.DATA_GOODS_ITEM -> loadProducts(data)
            Constants.DATA_PRICE -> loadPrices(data)
            Constants.DATA_IMAGE -> loadImages(data)
            Constants.DATA_CLIENT -> loadClients(data)
            Constants.DATA_CLIENT_LOCATION -> loadClientLocations(data)
            Constants.DATA_DEBT -> loadDebts(data)
            Constants.DATA_PAYMENT_TYPE -> loadPaymentTypes(data)
            else -> logger.e(logTag, "missed loader for ${data.first().getValueId()}")
        }
    }

    private suspend fun loadProducts(data: List<XMap>) {
        val prepared = data.map { Product.build(it) }
        val valid = prepared.filter { it.isValid() }
        if (valid.size < data.size) {
            val invalid = prepared.filter { !it.isValid() }
            for (item in invalid) logger.w(logTag, "invalid product: ${item.guid}")
        }
        dataExchangeDao.upsertProductList(valid)
    }

    private suspend fun loadPrices(data: List<XMap>) {
        val prepared = data.map { ProductPrice.build(it) }
        val valid = prepared.filter { it.isValid() }
        val diff = data.size - valid.size
        if (diff > 0) {
            val invalid = prepared.first { !it.isValid() }
            logger.w(logTag, "invalid product price: $diff: id=${invalid.productGuid} type=${invalid.priceType}")
        }
        dataExchangeDao.upsertPriceList(valid)

        val priceTypes = valid.map { it.priceType }.distinct()
        for (item in priceTypes) {
            val price = valid.first { it.priceType == item }
            val priceType = PriceType(
                databaseId = price.databaseId,
                timestamp = price.timestamp,
                priceType = item,
                description = data.first { it.getString("price_type") == item }.getString("price_name"),
            )
            dataExchangeDao.insertPriceType(priceType)
        }
    }

    private suspend fun loadImages(data: List<XMap>) {
        val prepared = data.map { ProductImage.build(it) }
        val valid = prepared.filter { it.isValid() }
        if (valid.size < data.size) {
            val invalid = prepared.filter { !it.isValid() }
            for (item in invalid) logger.w(logTag, "invalid product image: ${item.productGuid}")
        }
        dataExchangeDao.upsertImageList(valid)
    }

    private suspend fun loadClients(data: List<XMap>) {
        val prepared = data.map { Client.build(it) }
        val valid = prepared.filter { it.isValid() }
        if (valid.size < data.size) {
            val invalid = prepared.filter { !it.isValid() }
            for (item in invalid) logger.w(logTag, "invalid client: ${item.guid}")
        }
        dataExchangeDao.upsertClientList(valid)
    }

    private suspend fun loadClientLocations(data: List<XMap>) {
        val prepared = data.map { ClientLocation.build(it) }
        val valid = prepared.filter { it.isValid() }
        if (valid.size < data.size) {
            val invalid = prepared.filter { !it.isValid() }
            for (item in invalid) logger.w(logTag, "invalid client location: ${item.clientGuid}")
        }
        dataExchangeDao.upsertClientLocation(valid)
    }

    private suspend fun loadDebts(data: List<XMap>) {
        val prepared = data.map { Debt.build(it) }
        val valid = prepared.filter { it.isValid() }
        if (valid.size < data.size) {
            val invalid = prepared.filter { !it.isValid() }
            for (item in invalid) logger.w(logTag, "invalid debt: ${item.docId}")
        }
        dataExchangeDao.upsertDebtList(valid)
    }

    private suspend fun loadPaymentTypes(data: List<XMap>) {
        val prepared = data.map { PaymentType.build(it) }
        val valid = prepared.filter { it.isValid() }
        if (valid.size < data.size) {
            val invalid = prepared.filter { !it.isValid() }
            for (item in invalid) logger.w(logTag, "invalid payment type: ${item.paymentType}")
        }
        dataExchangeDao.upsertPaymentTypes(valid)
    }

    override suspend fun cleanUp(accountGuid: String, timestamp: Long) {
        try {
            var del = dataExchangeDao.deletePriceTypes(accountGuid, timestamp)
            del += dataExchangeDao.deletePrices(accountGuid, timestamp)
            del += dataExchangeDao.deleteImages(accountGuid, timestamp)
            del += dataExchangeDao.deleteProducts(accountGuid, timestamp)
            del += dataExchangeDao.deleteClients(accountGuid, timestamp)
            del += dataExchangeDao.deleteDebts(accountGuid, timestamp)
            if (del > 0) logger.d(logTag, "cleanUp: $del")
        } catch (e: Exception) {
            logger.e(logTag, "cleanUp: ${e.message}")
        }
    }

    override suspend fun saveSendResult(result: SendResult) {
        when (result.type) {
            Constants.DOCUMENT_ORDER -> dataExchangeDao.updateOrder(result.account, result.guid, result.status)
            Constants.DATA_CLIENT_IMAGE -> dataExchangeDao.updateClientImage(result.account, result.guid)
            Constants.DATA_CLIENT_LOCATION -> dataExchangeDao.updateClientLocation(result.account, result.guid)
        }
    }

    override suspend fun getOrders(accountGuid: String): List<Order> {
        return dataExchangeDao.getOrders(accountGuid) ?: emptyList()
    }

    override suspend fun getOrderContent(accountGuid: String, orderGuid: String): List<LOrderContent> {
        return dataExchangeDao.getOrderContent(accountGuid, orderGuid) ?: emptyList()
    }

    override suspend fun saveDebtContent(accountGuid: String, debtGuid: String, content: String) {
        dataExchangeDao.updateDebtContent(accountGuid, debtGuid, content)
    }

    override suspend fun getClientImages(accountGuid: String): List<ClientImage> {
        return dataExchangeDao.getClientImages(accountGuid) ?: emptyList()
    }

    override suspend fun getClientLocations(accountGuid: String): List<ClientLocation> {
        return dataExchangeDao.getClientLocations(accountGuid) ?: emptyList()
    }

}