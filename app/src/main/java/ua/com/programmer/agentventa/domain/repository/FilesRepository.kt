package ua.com.programmer.agentventa.domain.repository

import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.data.local.entity.ClientImage

interface FilesRepository {
    suspend fun saveClientImage(image: ClientImage)
    fun getClientImages(clientGuid: String): Flow<List<ClientImage>>
    fun getClientImage(guid: String): Flow<ClientImage?>
    suspend fun deleteClientImage(guid: String)
    suspend fun setAsDefault(image: ClientImage)
}