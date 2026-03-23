package ua.com.programmer.agentventa.data.local.dao

import androidx.room.Dao
import androidx.room.Query

@Dao
interface DiscountDao {

    @Query("SELECT d.discount FROM discounts d " +
            "WHERE d.db_guid = :dbGuid " +
            "AND d.client_guid IN (:clientGuid, '') " +
            "AND d.product_guid IN (:productGuid, :groupGuid, '') " +
            "ORDER BY " +
            "CASE d.client_guid WHEN :clientGuid THEN 0 ELSE 1 END, " +
            "CASE d.product_guid WHEN :productGuid THEN 0 WHEN :groupGuid THEN 1 ELSE 2 END " +
            "LIMIT 1")
    suspend fun getDiscount(
        dbGuid: String,
        clientGuid: String,
        productGuid: String,
        groupGuid: String
    ): Double?

    @Query("SELECT group_guid FROM products WHERE db_guid = :dbGuid AND guid = :productGuid LIMIT 1")
    suspend fun getProductGroupGuid(dbGuid: String, productGuid: String): String?

}
