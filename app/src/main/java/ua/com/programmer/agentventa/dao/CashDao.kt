package ua.com.programmer.agentventa.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.dao.entity.Cash
import ua.com.programmer.agentventa.dao.entity.DocumentTotals

@Dao
interface CashDao {

    @Query("""
        SELECT cash.*,
               client.description AS client_description,
               client.code2 AS client_code2
        FROM cash
        LEFT OUTER JOIN (SELECT guid, db_guid, description, code2 FROM clients) AS client
        ON client.guid=cash.client_guid AND client.db_guid=cash.db_guid
        WHERE cash.guid=:guid
    """)
    fun getDocument(guid: String): Flow<Cash?>

    @Query("""
        SELECT MAX(number)
        FROM cash
        WHERE db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1)
    """)
    suspend fun getMaxDocumentNumber(): Int?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(document: Cash): Long

    @Update(onConflict = OnConflictStrategy.IGNORE)
    suspend fun update(document: Cash): Int

    @Transaction
    suspend fun save(document: Cash): Boolean {
        if (update(document) == 0) {
            insert(document)
        }
        return true
    }

    @Delete
    suspend fun delete(document: Cash): Int

    @Query("""
        SELECT * FROM cash
        WHERE db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1 LIMIT 1)
        AND time >= :startTime AND time <= :endTime
        AND CASE :filter WHEN '' THEN 1=1 ELSE client_guid
        IN (SELECT guid FROM clients WHERE description LIKE :filter AND is_group=0
        AND db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1 LIMIT 1)) END
        ORDER BY time DESC LIMIT 200
    """)
    fun getDocumentsWithFilter(filter: String, startTime: Long, endTime: Long): Flow<List<Cash>>

    @Query("""
        SELECT * FROM cash
        WHERE db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1 LIMIT 1)
        AND CASE :filter WHEN '' THEN 1=1 ELSE client_guid
        IN (SELECT guid FROM clients WHERE description LIKE :filter AND is_group=0
        AND db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1 LIMIT 1)) END
        ORDER BY time DESC LIMIT 200
    """)
    fun getDocumentsWithFilter(filter: String): Flow<List<Cash>>

    @Query("""
        SELECT
            SUM(1) AS documents,
            SUM(CASE WHEN sum<0 THEN 1 ELSE 0 END) AS returns,
            SUM(0) AS weight,
            SUM(CASE WHEN sum>0 THEN sum ELSE 0 END) AS sum,
            0 AS discount,
            0 AS quantity,
            0 AS sumReturn
        FROM cash
        WHERE
            db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1 LIMIT 1)
            AND time >= :startTime AND time <= :endTime
            AND CASE :filter WHEN '' THEN 1=1 ELSE client_guid
            IN (SELECT guid FROM clients WHERE description LIKE :filter AND is_group=0
            AND db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1 LIMIT 1)) END
    """)
    fun getDocumentsTotals(filter: String, startTime: Long, endTime: Long): Flow<List<DocumentTotals>>

    @Query("""
        SELECT
            SUM(1) AS documents,
            SUM(CASE WHEN sum<0 THEN 1 ELSE 0 END) AS returns,
            SUM(0) AS weight,
            SUM(CASE WHEN sum>0 THEN sum ELSE 0 END) AS sum,
            0 AS discount,
            0 AS quantity,
            0 AS sumReturn
        FROM cash
        WHERE
            db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1 LIMIT 1)
            AND CASE :filter WHEN '' THEN 1=1 ELSE client_guid
            IN (SELECT guid FROM clients WHERE description LIKE :filter AND is_group=0
            AND db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1 LIMIT 1)) END
    """)
    fun getDocumentsTotals(filter: String): Flow<List<DocumentTotals>>

    @Query("UPDATE cash SET company_guid=:companyGuid, company=:companyDescription WHERE guid=:guid")
    suspend fun setCompany(guid: String, companyGuid: String, companyDescription: String)

    @Query("UPDATE cash SET client_guid=:clientGuid, client=:clientDescription WHERE guid=:guid")
    suspend fun setClient(guid: String, clientGuid: String, clientDescription: String)
}