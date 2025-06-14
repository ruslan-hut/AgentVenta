package ua.com.programmer.agentventa.repository

import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.dao.entity.Cash
import ua.com.programmer.agentventa.dao.entity.Client
import ua.com.programmer.agentventa.dao.entity.Company
import java.util.Date

interface CashRepository: DocumentRepository<Cash> {
    override fun getDocument(guid: String): Flow<Cash>
    override suspend fun newDocument(): Cash?
    override fun getDocuments(filter: String, listDate: Date?): Flow<List<Cash>>
    override suspend fun updateDocument(document: Cash): Boolean
    suspend fun setCompany(guid: String, company: Company)
    suspend fun setClient(guid: String, client: Client)
}