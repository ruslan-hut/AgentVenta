package ua.com.programmer.agentventa.dao.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import ua.com.programmer.agentventa.dao.CashDao
import ua.com.programmer.agentventa.dao.UserAccountDao
import ua.com.programmer.agentventa.dao.entity.Cash
import ua.com.programmer.agentventa.dao.entity.Client
import ua.com.programmer.agentventa.dao.entity.Company
import ua.com.programmer.agentventa.dao.entity.DocumentTotals
import ua.com.programmer.agentventa.extensions.asFilter
import ua.com.programmer.agentventa.extensions.beginOfDay
import ua.com.programmer.agentventa.extensions.endOfDay
import ua.com.programmer.agentventa.repository.CashRepository
import ua.com.programmer.agentventa.repository.UserAccountRepository
import ua.com.programmer.agentventa.utility.UtilsInterface
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class CashRepositoryImpl @Inject constructor(
    private val dao: CashDao,
    private val userAccountDao: UserAccountDao,
    private val userAccountRepository: UserAccountRepository,
    private val utils: UtilsInterface
): CashRepository {

    private suspend fun getCurrentDbGuid(): String = userAccountRepository.currentAccountGuid.first()

    override fun getDocument(guid: String): Flow<Cash> {
        return dao.getDocument(guid).map { it ?: Cash() }
    }

    override suspend fun newDocument(): Cash? {
        val currentDbGuid = getCurrentDbGuid()
        val number = (dao.getMaxDocumentNumber(currentDbGuid) ?: 0) + 1
        val time = utils.currentTime()

        val document = Cash(
            databaseId = currentDbGuid,
            number = number,
            guid = UUID.randomUUID().toString(),
            time = time,
            date = utils.dateLocal(time),
        )

        return if (dao.save(document)) document else null
    }

    override fun getDocuments(filter: String, listDate: Date?): Flow<List<Cash>> {
        return userAccountRepository.currentAccountGuid.flatMapLatest { currentDbGuid ->
            if (listDate == null) {
                dao.getDocumentsWithFilter(currentDbGuid, filter.asFilter())
            } else {
                val startTime = listDate.beginOfDay()
                val endTime = listDate.endOfDay()
                dao.getDocumentsWithFilter(currentDbGuid, filter.asFilter(), startTime, endTime)
            }
        }
    }

    override suspend fun updateDocument(document: Cash): Boolean {
        return dao.update(document) > 0
    }

    override suspend fun setCompany(
        guid: String,
        company: Company
    ) {
        dao.setCompany(guid, company.guid, company.description)
    }

    override suspend fun setClient(
        guid: String,
        client: Client
    ) {
        dao.setClient(guid, client.guid, client.description)
    }

    override suspend fun deleteDocument(document: Cash): Boolean {
        return dao.delete(document) > 0
    }

    override fun getDocumentListTotals(filter: String, listDate: Date?): Flow<List<DocumentTotals>> {
        return userAccountRepository.currentAccountGuid.flatMapLatest { currentDbGuid ->
            if (listDate == null) {
                dao.getDocumentsTotals(currentDbGuid, filter.asFilter())
            } else {
                val startTime = listDate.beginOfDay()
                val endTime = listDate.endOfDay()
                dao.getDocumentsTotals(currentDbGuid, filter.asFilter(), startTime, endTime)
            }
        }
    }
}