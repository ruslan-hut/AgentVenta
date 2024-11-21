package ua.com.programmer.agentventa.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.dao.entity.DocumentTotals
import ua.com.programmer.agentventa.dao.entity.Task

@Dao
interface TaskDao {

    @Query("SELECT " +
            "tasks.*," +
            "client.description AS client_description," +
            "client.code2 AS client_code2 " +
            "FROM tasks " +
            "LEFT OUTER JOIN (SELECT guid, db_guid, description, code2 FROM clients) AS client " +
            "ON client.guid=tasks.client_guid AND client.db_guid=tasks.db_guid " +
            "WHERE tasks.guid=:guid")
    fun getDocument(guid: String): Flow<Task>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(document: Task): Long

    @Update(onConflict = OnConflictStrategy.IGNORE)
    suspend fun update(document: Task): Int

    @Transaction
    suspend fun save(document: Task): Boolean {
        if (update(document) == 0) {
            insert(document)
        }
        return true
    }

    @Delete
    suspend fun delete(document: Task): Int

    @Query("SELECT * FROM tasks " +
            "WHERE db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1 LIMIT 1) " +
            "AND time >= :startTime AND time <= :endTime " +
            "AND CASE :filter WHEN '' THEN 1=1 ELSE client_guid " +
            "IN (SELECT guid FROM clients WHERE description LIKE :filter AND is_group=0 " +
            "AND db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1 LIMIT 1)) " +
            "OR description LIKE :filter END " +
            "ORDER BY time DESC LIMIT 200")
    fun getDocumentsWithFilter(filter: String, startTime: Long, endTime: Long): Flow<List<Task>>

    @Query("SELECT * FROM tasks " +
            "WHERE db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1 LIMIT 1) " +
            "AND CASE :filter WHEN '' THEN 1=1 ELSE client_guid " +
            "IN (SELECT guid FROM clients WHERE description LIKE :filter AND is_group=0 " +
            "AND db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1 LIMIT 1)) " +
            "OR description LIKE :filter END " +
            "ORDER BY time DESC LIMIT 200")
    fun getDocumentsWithFilter(filter: String): Flow<List<Task>>

    @Query("SELECT " +
            "SUM(CASE is_done WHEN 1 THEN 1 ELSE 0 END) AS documents," +
            "SUM(CASE is_done WHEN 0 THEN 1 ELSE 0 END) AS returns," +
            "SUM(0) AS weight," +
            "SUM(0) AS sum," +
            "0 AS discount," +
            "0 AS quantity," +
            "0 AS sumReturn " +
            "FROM tasks " +
            "WHERE " +
            "db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1 LIMIT 1) " +
            "AND time >= :startTime AND time <= :endTime " +
            "AND CASE :filter WHEN '' THEN 1=1 ELSE client_guid " +
            "IN (SELECT guid FROM clients WHERE description LIKE :filter AND is_group=0 " +
            "AND db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1 LIMIT 1)) " +
            "OR description LIKE :filter END")
    fun getDocumentsTotals(filter: String, startTime: Long, endTime: Long): Flow<List<DocumentTotals>>

    @Query("SELECT " +
            "SUM(CASE is_done WHEN 1 THEN 1 ELSE 0 END) AS documents," +
            "SUM(CASE is_done WHEN 0 THEN 1 ELSE 0 END) AS returns," +
            "SUM(0) AS weight," +
            "SUM(0) AS sum," +
            "0 AS discount," +
            "0 AS quantity," +
            "0 AS sumReturn " +
            "FROM tasks " +
            "WHERE " +
            "db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1 LIMIT 1) " +
            "AND CASE :filter WHEN '' THEN 1=1 ELSE client_guid " +
            "IN (SELECT guid FROM clients WHERE description LIKE :filter AND is_group=0 " +
            "AND db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1 LIMIT 1)) " +
            "OR description LIKE :filter END")
    fun getDocumentsTotals(filter: String): Flow<List<DocumentTotals>>
}