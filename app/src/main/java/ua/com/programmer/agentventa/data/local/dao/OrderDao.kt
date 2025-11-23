package ua.com.programmer.agentventa.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.data.local.entity.Client
import ua.com.programmer.agentventa.data.local.entity.Company
import ua.com.programmer.agentventa.data.local.entity.DocumentTotals
import ua.com.programmer.agentventa.data.local.entity.LOrderContent
import ua.com.programmer.agentventa.data.local.entity.Order
import ua.com.programmer.agentventa.data.local.entity.OrderContent
import ua.com.programmer.agentventa.data.local.entity.PaymentType
import ua.com.programmer.agentventa.data.local.entity.PriceType
import ua.com.programmer.agentventa.data.local.entity.Store

@Dao
interface OrderDao {

    @Query("SELECT " +
            "orders.*," +
            "IFNULL(client.description, '<?>') AS client_description," +
            "IFNULL(client.code2, '<?>') AS client_code2," +
            "IFNULL(payment.description, '<?>') AS payment " +
            "FROM orders " +
            "LEFT OUTER JOIN (SELECT guid, db_guid, description, code2 FROM clients) AS client " +
            "ON client.guid=orders.client_guid AND client.db_guid=orders.db_guid " +
            "LEFT OUTER JOIN (SELECT payment_type, db_guid, description FROM payment_types) AS payment " +
            "ON payment.payment_type=orders.payment_type AND payment.db_guid=orders.db_guid " +
            "WHERE orders.guid=:guid")
    fun getDocument(guid: String): Flow<Order?>

    @Query("SELECT * FROM orders WHERE guid=:guid")
    suspend fun getOrder(guid: String): Order?

    @Query("SELECT number " +
            "FROM orders " +
            "WHERE db_guid = :currentDbGuid " +
            "ORDER BY time DESC LIMIT 1")
    suspend fun getLastDocumentNumber(currentDbGuid: String): Int?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(document: Order): Long

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(document: Order): Int

    @Transaction
    suspend fun save(document: Order): Boolean {
        if (update(document) == 0) {
            insert(document)
        }
        return true
    }

    @Delete
    suspend fun delete(document: Order): Int

    @Query("SELECT * FROM orders WHERE db_guid=:id ORDER BY time DESC LIMIT 200")
    fun getAllDocuments(id: String): Flow<List<Order>>

    @Query("SELECT * FROM orders " +
            "WHERE db_guid = :currentDbGuid " +
            "AND time >= :startTime AND time <= :endTime " +
            "AND CASE :filter WHEN '' THEN 1=1 ELSE client_description LIKE :filter END " +
            "ORDER BY time DESC LIMIT 200")
    fun getDocumentsWithFilter(currentDbGuid: String, filter: String, startTime: Long, endTime: Long): Flow<List<Order>>

    @Query("SELECT * FROM orders " +
            "WHERE db_guid = :currentDbGuid " +
            "AND CASE :filter WHEN '' THEN 1=1 ELSE client_description LIKE :filter END " +
            "ORDER BY time DESC LIMIT 200")
    fun getDocumentsWithFilter(currentDbGuid: String, filter: String): Flow<List<Order>>

    @Query("SELECT " +
            "SUM(1) AS documents," +
            "SUM(CASE WHEN is_return>0 THEN 1 ELSE 0 END) AS returns," +
            "SUM(weight) AS weight," +
            "SUM(price) AS sum," +
            "0 AS discount," +
            "0 AS quantity," +
            "0 AS sumReturn " +
            "FROM orders " +
            "WHERE " +
            "db_guid = :currentDbGuid " +
            "AND time >= :startTime AND time <= :endTime " +
            "AND CASE :filter WHEN '' THEN 1=1 ELSE client_description LIKE :filter END ORDER BY time DESC")
    fun getDocumentsTotals(currentDbGuid: String, filter: String, startTime: Long, endTime: Long): Flow<List<DocumentTotals>>

    @Query("SELECT " +
            "SUM(1) AS documents," +
            "SUM(CASE WHEN is_return>0 THEN 1 ELSE 0 END) AS returns," +
            "SUM(weight) AS weight," +
            "SUM(price) AS sum," +
            "0 AS discount," +
            "0 AS quantity," +
            "0 AS sumReturn " +
            "FROM orders " +
            "WHERE " +
            "db_guid = :currentDbGuid " +
            "AND CASE :filter WHEN '' THEN 1=1 ELSE client_description LIKE :filter END ORDER BY time DESC")
    fun getDocumentsTotals(currentDbGuid: String, filter: String): Flow<List<DocumentTotals>>

    @Query("SELECT " +
            "1 AS documents," +
            "0 AS returns," +
            "SUM(weight) AS weight," +
            "SUM(sum) AS sum," +
            "SUM(discount) AS discount," +
            "SUM(quantity) AS quantity," +
            "0 AS sumReturn " +
            "FROM order_content " +
            "WHERE order_guid=:guid")
    suspend fun getDocumentTotals(guid: String): DocumentTotals?

    @Query("SELECT " +
            "1 AS documents," +
            "0 AS returns," +
            "SUM(weight) AS weight," +
            "SUM(sum) AS sum," +
            "SUM(discount) AS discount," +
            "SUM(quantity) AS quantity," +
            "0 AS sumReturn " +
            "FROM order_content " +
            "WHERE order_guid=:guid")
    fun watchDocumentTotals(guid: String): Flow<DocumentTotals?>

    @Query("SELECT * FROM order_content WHERE order_guid=:orderGuid AND product_guid=:productGuid")
    suspend fun getContentLine(orderGuid: String, productGuid: String): OrderContent?

    @Query("DELETE FROM order_content WHERE order_guid=:orderGuid AND product_guid=:productGuid")
    suspend fun deleteContentLine(orderGuid: String, productGuid: String): Int

    @Query("DELETE FROM order_content WHERE order_guid=:orderGuid")
    suspend fun clearContent(orderGuid: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContentLine(line: OrderContent): Long

    @Query("SELECT " +
            "content._id AS id," +
            "content.order_guid AS orderGuid," +
            "content.product_guid AS productGuid," +
            "IFNULL(product.description, '<?>') AS description," +
            "IFNULL(product.code2, '<?>') AS code," +
            "IFNULL(product.groupName, '') AS groupName," +
            "content.unit_code AS unit," +
            "content.quantity," +
            "content.weight," +
            "content.price," +
            "content.sum," +
            "content.discount," +
            "content.is_packed AS isPacked," +
            "content.is_demand AS isDemand " +
            "FROM order_content AS content " +
            "LEFT OUTER JOIN (" +
                "SELECT " +
                "products.guid, " +
                "products.description, " +
                "products.code2, " +
                "products.group_guid," +
                "IFNULL(product_groups.description, '') AS groupName " +
                "FROM products " +
                "LEFT OUTER JOIN (SELECT description, guid, db_guid FROM products WHERE is_group=1) AS product_groups " +
                    "ON products.group_guid=product_groups.guid AND products.db_guid=product_groups.db_guid " +
                "WHERE products.db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1)) AS product " +
                "ON product.guid=content.product_guid " +
            "WHERE content.order_guid=:orderGuid")
    fun getOrderContent(orderGuid: String): Flow<List<LOrderContent>?>

    @Query("SELECT " +
            "content._id AS id," +
            "content.order_guid AS orderGuid," +
            "content.product_guid AS productGuid," +
            "IFNULL(product.description, '<?>') AS description," +
            "IFNULL(product.code2, '<?>') AS code," +
            "IFNULL(product.groupName, '') AS groupName," +
            "content.unit_code AS unit," +
            "content.quantity," +
            "content.weight," +
            "content.price," +
            "content.sum," +
            "content.discount," +
            "content.is_packed AS isPacked," +
            "content.is_demand AS isDemand " +
            "FROM order_content AS content " +
            "LEFT OUTER JOIN (" +
            "SELECT " +
            "products.guid, " +
            "products.description, " +
            "products.code2, " +
            "products.group_guid," +
            "IFNULL(product_groups.description, '') AS groupName " +
            "FROM products " +
            "LEFT OUTER JOIN (SELECT description, guid, db_guid FROM products WHERE is_group=1) AS product_groups " +
            "ON products.group_guid=product_groups.guid AND products.db_guid=product_groups.db_guid " +
            "WHERE products.db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1)) AS product " +
            "ON product.guid=content.product_guid " +
            "WHERE content.order_guid=:orderGuid")
    suspend fun getContent(orderGuid: String): List<LOrderContent>?

    // get order for clientGuid before given time
    @Query("SELECT * FROM orders " +
            "WHERE client_guid=:clientGuid " +
            "AND time < :time " +
            "AND is_processed > 0 " +
            "AND db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1) " +
            "ORDER BY time DESC LIMIT 1")
    suspend fun getOrder(clientGuid: String, time: Long): Order?

    @Query("SELECT * FROM order_content " +
            "WHERE order_guid IN " +
            "(SELECT guid FROM orders " +
            "WHERE time >= :startTime AND time <= :endTime " +
            "AND client_guid = :clientGuid " +
            "AND is_processed > 0 " +
            "AND db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1))")
    suspend fun getContentInPeriod(startTime: Long, endTime: Long, clientGuid: String): List<OrderContent>

    @Query("SELECT * FROM price_types WHERE db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1)")
    suspend fun getPriceTypes(): List<PriceType>?

    @Query("SELECT * FROM companies WHERE db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1)")
    suspend fun getCompanies(): List<Company>?

    @Query("SELECT * FROM stores WHERE db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1)")
    suspend fun getStores(): List<Store>?

    @Query("SELECT * FROM payment_types WHERE db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1)")
    suspend fun getPaymentTypes(): List<PaymentType>?

    // get client by guid
    @Query("SELECT * FROM clients WHERE guid=:guid AND db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1)")
    suspend fun getClient(guid: String): Client?

    @Query("UPDATE orders SET company_guid=:companyGuid, company=:companyDescription WHERE guid=:guid")
    suspend fun setCompany(guid: String, companyGuid: String, companyDescription: String)

    @Query("UPDATE orders SET store_guid=:storeGuid, store=:storeDescription WHERE guid=:guid")
    suspend fun setStore(guid: String, storeGuid: String, storeDescription: String)

    @Query("UPDATE orders SET client_guid=:clientGuid, client_description=:clientDescription WHERE guid=:guid")
    suspend fun setClient(guid: String, clientGuid: String, clientDescription: String)
}