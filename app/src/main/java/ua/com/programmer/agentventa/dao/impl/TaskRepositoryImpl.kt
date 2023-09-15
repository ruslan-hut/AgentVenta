package ua.com.programmer.agentventa.dao.impl

import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.dao.TaskDao
import ua.com.programmer.agentventa.dao.UserAccountDao
import ua.com.programmer.agentventa.dao.entity.DocumentTotals
import ua.com.programmer.agentventa.dao.entity.Task
import ua.com.programmer.agentventa.extensions.asFilter
import ua.com.programmer.agentventa.extensions.beginOfDay
import ua.com.programmer.agentventa.extensions.endOfDay
import ua.com.programmer.agentventa.repository.TaskRepository
import ua.com.programmer.agentventa.utility.Utils
import java.util.Date
import javax.inject.Inject

class TaskRepositoryImpl @Inject constructor(
    private val dao: TaskDao,
    private val userAccountDao: UserAccountDao
    ): TaskRepository {

    private val utils = Utils()
    override fun getDocument(guid: String): Flow<Task> {
        return dao.getDocument(guid)
    }

    override suspend fun newDocument(): Task? {
        val time = utils.currentTime()
        val dbGuid = userAccountDao.getCurrent()?.guid ?: return null

        val document = Task(
            databaseId = dbGuid,
            guid = java.util.UUID.randomUUID().toString(),
            time = time,
            date = utils.dateLocal(time),
        )

        return if (dao.save(document)) document else null
    }

    override fun getDocuments(filter: String, listDate: Date?): Flow<List<Task>> {
        if (listDate == null) return dao.getDocumentsWithFilter(filter.asFilter())
        val startTime = listDate.beginOfDay()
        val endTime = listDate.endOfDay()
        return dao.getDocumentsWithFilter(filter.asFilter(), startTime, endTime)
    }

    override suspend fun updateDocument(document: Task): Boolean {
        return dao.update(document) > 0
    }

    override suspend fun deleteDocument(document: Task): Boolean {
        return dao.delete(document) > 0
    }

    override fun getDocumentListTotals(filter: String, listDate: Date?): Flow<List<DocumentTotals>> {
        if (listDate == null) return dao.getDocumentsTotals(filter.asFilter())
        val startTime = listDate.beginOfDay()
        val endTime = listDate.endOfDay()
        return dao.getDocumentsTotals(filter.asFilter(), startTime, endTime)
    }

}