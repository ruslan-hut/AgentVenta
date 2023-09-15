package ua.com.programmer.agentventa.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.dao.entity.PriceType
import ua.com.programmer.agentventa.dao.entity.UserAccount

@Dao
interface UserAccountDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(account: UserAccount): Long

    @Update
    suspend fun update(account: UserAccount): Int

    @Query("SELECT * FROM user_accounts ORDER BY description")
    fun getAll(): Flow<List<UserAccount>>

    @Query("UPDATE user_accounts SET is_current=0")
    suspend fun resetIsCurrent()

    @Query("UPDATE user_accounts SET is_current=1 WHERE guid=:guid")
    suspend fun setIsCurrent(guid: String)

    @Query("SELECT * FROM user_accounts WHERE guid=:guid")
    fun getByGuid(guid: String): Flow<UserAccount?>

    @Query("SELECT * FROM user_accounts WHERE is_current=1 LIMIT 1")
    suspend fun getCurrent(): UserAccount?

    @Query("SELECT * FROM user_accounts WHERE is_current=1 LIMIT 1")
    fun watchCurrent(): Flow<UserAccount?>

    @Query("DELETE FROM user_accounts WHERE guid=:guid")
    suspend fun deleteByGuid(guid: String): Int

    @Query("DELETE FROM products WHERE db_guid=:guid")
    suspend fun deleteByGuidProduct(guid: String): Int

    @Query("DELETE FROM clients WHERE db_guid=:guid")
    suspend fun deleteByGuidClients(guid: String): Int

    @Query("DELETE FROM client_locations WHERE db_guid=:guid")
    suspend fun deleteByGuidClientLocations(guid: String): Int

    @Query("DELETE FROM orders WHERE db_guid=:guid")
    suspend fun deleteByGuidOrders(guid: String): Int

    @Query("DELETE FROM order_content WHERE order_guid IN (SELECT guid FROM orders WHERE db_guid=:guid)")
    suspend fun deleteByGuidOrderContent(guid: String): Int

    @Query("DELETE FROM cash WHERE db_guid=:guid")
    suspend fun deleteByGuidCash(guid: String): Int

    @Query("SELECT * FROM price_types WHERE db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1)")
    fun getPriceTypes(): Flow<List<PriceType>?>

    @Query("SELECT COUNT(*) FROM user_accounts")
    suspend fun numberOfAccounts(): Int
}