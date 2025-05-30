package ua.com.programmer.agentventa.repository

import ua.com.programmer.agentventa.dao.entity.Cash
import ua.com.programmer.agentventa.dao.entity.ClientImage
import ua.com.programmer.agentventa.dao.entity.ClientLocation
import ua.com.programmer.agentventa.dao.entity.LOrderContent
import ua.com.programmer.agentventa.dao.entity.Order
import ua.com.programmer.agentventa.http.SendResult
import ua.com.programmer.agentventa.utility.XMap

interface DataExchangeRepository {
    suspend fun saveData(data: List<XMap>)
    suspend fun cleanUp(accountGuid: String, timestamp: Long)
    suspend fun saveSendResult(result: SendResult)
    suspend fun getOrders(accountGuid: String): List<Order>
    suspend fun getOrderContent(accountGuid: String, orderGuid: String): List<LOrderContent>
    suspend fun saveDebtContent(accountGuid: String, debtGuid: String, content: String)
    suspend fun getClientImages(accountGuid: String): List<ClientImage>
    suspend fun getClientLocations(accountGuid: String): List<ClientLocation>
    suspend fun getCash(accountGuid: String): List<Cash>
}