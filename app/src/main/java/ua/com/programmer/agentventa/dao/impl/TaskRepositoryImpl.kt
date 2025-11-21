package ua.com.programmer.agentventa.dao.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import ua.com.programmer.agentventa.dao.TaskDao
import ua.com.programmer.agentventa.dao.UserAccountDao
import ua.com.programmer.agentventa.dao.entity.DocumentTotals
import ua.com.programmer.agentventa.dao.entity.Task
import ua.com.programmer.agentventa.extensions.asFilter
import ua.com.programmer.agentventa.extensions.beginOfDay
import ua.com.programmer.agentventa.extensions.endOfDay
import ua.com.programmer.agentventa.repository.TaskRepository
import ua.com.programmer.agentventa.repository.UserAccountRepository
import ua.com.programmer.agentventa.utility.UtilsInterface
import java.util.Date
import javax.inject.Inject

class TaskRepositoryImpl @Inject constructor(
    private val dao: TaskDao,
    private val userAccountDao: UserAccountDao,
    private val userAccountRepository: UserAccountRepository,
    private val utils: UtilsInterface
    ): TaskRepository {

    private suspend fun getCurrentDbGuid(): String = userAccountRepository.currentAccountGuid.first()

    override fun getDocument(guid: String): Flow<Task> {
        return dao.getDocument(guid)
    }

    override suspend fun newDocument(): Task? {
        val currentDbGuid = getCurrentDbGuid()
        val time = utils.currentTime()

        val document = Task(
            databaseId = currentDbGuid,
            guid = java.util.UUID.randomUUID().toString(),
            time = time,
            date = utils.dateLocal(time),
        )

        return if (dao.save(document)) document else null
    }

    override fun getDocuments(filter: String, listDate: Date?): Flow<List<Task>> {
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

    override suspend fun updateDocument(document: Task): Boolean {
        return dao.update(document) > 0
    }

    override suspend fun deleteDocument(document: Task): Boolean {
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