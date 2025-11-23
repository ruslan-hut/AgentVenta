package ua.com.programmer.agentventa.domain.repository

import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.data.local.entity.Task
import java.util.Date

interface TaskRepository: DocumentRepository<Task> {
    override fun getDocument(guid: String): Flow<Task>
    override suspend fun newDocument(): Task?
    override fun getDocuments(filter: String, listDate: Date?): Flow<List<Task>>
    override suspend fun updateDocument(document: Task): Boolean
    suspend fun insertOrUpdateDocument(document: Task): Boolean
}