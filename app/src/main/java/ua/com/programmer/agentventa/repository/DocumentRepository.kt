package ua.com.programmer.agentventa.repository

import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.dao.entity.DocumentTotals
import java.util.Date

interface DocumentRepository<T> {
    fun getDocument(guid: String): Flow<T>
    suspend fun newDocument(): T?
    fun getDocuments(filter: String, listDate: Date?): Flow<List<T>>
    suspend fun updateDocument(document: T): Boolean
    suspend fun deleteDocument(document: T): Boolean
    fun getDocumentListTotals(filter: String, listDate: Date?): Flow<List<DocumentTotals>>
}