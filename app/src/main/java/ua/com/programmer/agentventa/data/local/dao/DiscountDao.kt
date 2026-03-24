package ua.com.programmer.agentventa.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.data.local.entity.DiscountMatch
import ua.com.programmer.agentventa.data.local.entity.LDiscount

@Dao
interface DiscountDao {

    /**
     * Returns all matching discount rules for the given client and product ancestor chain.
     * Caller must pick the best match by ancestor priority.
     *
     * @param ancestors list of GUIDs to match: [productGuid, parentGroupGuid, ..., ""]
     * @param clientGuid the client (also matches "" = any client)
     * @return list of (client_guid, product_guid, discount) tuples
     */
    @Query("SELECT client_guid AS clientGuid, product_guid AS productGuid, discount " +
            "FROM discounts " +
            "WHERE db_guid = :dbGuid " +
            "AND client_guid IN (:clientGuid, '') " +
            "AND product_guid IN (:ancestors)")
    suspend fun getMatchingDiscounts(
        dbGuid: String,
        clientGuid: String,
        ancestors: List<String>
    ): List<DiscountMatch>

    @Query("SELECT group_guid FROM products WHERE db_guid = :dbGuid AND guid = :guid LIMIT 1")
    suspend fun getProductGroupGuid(dbGuid: String, guid: String): String?

    @Query("SELECT " +
            "d.product_guid AS productGuid, " +
            "CASE " +
            "WHEN d.product_guid = '' THEN '' " +
            "ELSE IFNULL(p.description, d.product_guid) " +
            "END AS description, " +
            "d.discount, " +
            "IFNULL(p.is_group, 0) AS isGroup " +
            "FROM discounts d " +
            "LEFT OUTER JOIN products p ON d.product_guid = p.guid AND d.db_guid = p.db_guid " +
            "WHERE d.db_guid = :dbGuid " +
            "AND d.client_guid IN (:clientGuid, '') " +
            "ORDER BY " +
            "CASE d.client_guid WHEN :clientGuid THEN 0 ELSE 1 END, " +
            "IFNULL(p.is_group, 0) DESC, " +
            "description ASC")
    fun getClientDiscounts(dbGuid: String, clientGuid: String): Flow<List<LDiscount>>

}
