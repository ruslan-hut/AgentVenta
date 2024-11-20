package ua.com.programmer.agentventa.dao

import androidx.room.Dao
import androidx.room.Query

@Dao
interface CommonDao {

    @Query("DELETE FROM orders WHERE time < :from")
    suspend fun deleteOrders(from: Long): Int

    @Query("DELETE FROM order_content " +
            "WHERE order_guid IN (SELECT guid FROM orders WHERE time > :from)")
    suspend fun deleteOrderContent(from: Long): Int

    @Query("DELETE FROM cash WHERE time < :from")
    suspend fun deleteCash(from: Long): Int

    @Query("DELETE FROM tasks WHERE time < :from")
    suspend fun deleteTasks(from: Long): Int

    @Query("DELETE FROM locations WHERE time < :from")
    suspend fun deleteLocations(from: Long): Int

}