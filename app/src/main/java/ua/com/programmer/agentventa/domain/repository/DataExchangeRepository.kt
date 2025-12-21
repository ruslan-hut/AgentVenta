package ua.com.programmer.agentventa.domain.repository

import ua.com.programmer.agentventa.data.local.entity.Cash
import ua.com.programmer.agentventa.data.local.entity.ClientImage
import ua.com.programmer.agentventa.data.local.entity.ClientLocation
import ua.com.programmer.agentventa.data.local.entity.LOrderContent
import ua.com.programmer.agentventa.data.local.entity.Order
import ua.com.programmer.agentventa.data.remote.SendResult
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

    // WebSocket document status updates
    suspend fun markOrderSentViaWebSocket(accountGuid: String, orderGuid: String): Int
    suspend fun markCashSentViaWebSocket(accountGuid: String, docGuid: String): Int
    suspend fun markImageSentViaWebSocket(accountGuid: String, imageGuid: String): Int
    suspend fun markLocationSentViaWebSocket(accountGuid: String, clientGuid: String): Int
}