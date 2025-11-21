package ua.com.programmer.agentventa.dao.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import ua.com.programmer.agentventa.dao.ClientDao
import ua.com.programmer.agentventa.dao.entity.ClientImage
import ua.com.programmer.agentventa.repository.FilesRepository
import ua.com.programmer.agentventa.repository.UserAccountRepository
import javax.inject.Inject

class FilesRepositoryImpl @Inject constructor(
    private val clientDao: ClientDao,
    private val userAccountRepository: UserAccountRepository
): FilesRepository {

    private suspend fun getCurrentDbGuid(): String = userAccountRepository.currentAccountGuid.first()
    override suspend fun saveClientImage(image: ClientImage) {
        clientDao.upsertClientImage(image)
    }

    override fun getClientImages(clientGuid: String): Flow<List<ClientImage>> {
        return userAccountRepository.currentAccountGuid.flatMapLatest { currentDbGuid ->
            clientDao.getClientImages(currentDbGuid, clientGuid)
        }
    }

    override fun getClientImage(guid: String): Flow<ClientImage?> {
        return userAccountRepository.currentAccountGuid.flatMapLatest { currentDbGuid ->
            clientDao.getClientImage(currentDbGuid, guid)
        }
    }

    override suspend fun deleteClientImage(guid: String) {
        val currentDbGuid = getCurrentDbGuid()
        clientDao.deleteClientImage(currentDbGuid, guid)
    }

    override suspend fun setAsDefault(image: ClientImage) {
        val currentDbGuid = getCurrentDbGuid()
        clientDao.makeImageDefault(currentDbGuid, image.clientGuid, image.guid)
    }
}