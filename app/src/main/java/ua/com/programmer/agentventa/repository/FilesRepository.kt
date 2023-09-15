package ua.com.programmer.agentventa.repository

import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.dao.entity.ClientImage

interface FilesRepository {
    suspend fun saveClientImage(image: ClientImage)
    fun getClientImages(clientGuid: String): Flow<List<ClientImage>>
    fun getClientImage(guid: String): Flow<ClientImage?>
    suspend fun deleteClientImage(guid: String)
    suspend fun setAsDefault(image: ClientImage)
}