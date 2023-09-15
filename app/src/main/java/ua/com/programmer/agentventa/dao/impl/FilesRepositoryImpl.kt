package ua.com.programmer.agentventa.dao.impl

import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.dao.ClientDao
import ua.com.programmer.agentventa.dao.entity.ClientImage
import ua.com.programmer.agentventa.repository.FilesRepository
import javax.inject.Inject

class FilesRepositoryImpl @Inject constructor(private val clientDao: ClientDao): FilesRepository {
    override suspend fun saveClientImage(image: ClientImage) {
        clientDao.upsertClientImage(image)
    }

    override fun getClientImages(clientGuid: String): Flow<List<ClientImage>> {
        return clientDao.getClientImages(clientGuid)
    }

    override fun getClientImage(guid: String): Flow<ClientImage?> {
        return clientDao.getClientImage(guid)
    }

    override suspend fun deleteClientImage(guid: String) {
        clientDao.deleteClientImage(guid)
    }

    override suspend fun setAsDefault(image: ClientImage) {
        clientDao.resetDefault(image.clientGuid)
        clientDao.setAsDefault(image.guid)
    }
}