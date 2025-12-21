package ua.com.programmer.agentventa.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import ua.com.programmer.agentventa.data.local.entity.Cash
import ua.com.programmer.agentventa.data.local.entity.Client
import ua.com.programmer.agentventa.data.local.entity.ClientImage
import ua.com.programmer.agentventa.data.local.entity.ClientLocation
import ua.com.programmer.agentventa.data.local.entity.Company
import ua.com.programmer.agentventa.data.local.entity.Debt
import ua.com.programmer.agentventa.data.local.entity.LOrderContent
import ua.com.programmer.agentventa.data.local.entity.Order
import ua.com.programmer.agentventa.data.local.entity.PaymentType
import ua.com.programmer.agentventa.data.local.entity.PriceType
import ua.com.programmer.agentventa.data.local.entity.Product
import ua.com.programmer.agentventa.data.local.entity.ProductImage
import ua.com.programmer.agentventa.data.local.entity.ProductPrice
import ua.com.programmer.agentventa.data.local.entity.Rest
import ua.com.programmer.agentventa.data.local.entity.Store

@Dao
interface DataExchangeDao {

    //----------------------------------------------------- PRODUCTS

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProductList(products: List<Product>): List<Long>

    @Update(onConflict = OnConflictStrategy.IGNORE)
    suspend fun updateProductList(products: List<Product>): Int

    @Transaction
    suspend fun upsertProductList(products: List<Product>) {
        val updateCount = updateProductList(products)
        if (updateCount != products.size) {
            insertProductList(products)
        }
    }

    //----------------------------------------------------- PRICE LIST

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPriceList(price: List<ProductPrice>): List<Long>

    @Update(onConflict = OnConflictStrategy.IGNORE)
    suspend fun updatePriceList(price: List<ProductPrice>): Int

    @Transaction
    suspend fun upsertPriceList(price: List<ProductPrice>) {
        val updateCount = updatePriceList(price)
        if (updateCount != price.size) {
            insertPriceList(price)
        }
    }

    //----------------------------------------------------- PRICE LIST

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRests(rest: List<Rest>): List<Long>

    @Update(onConflict = OnConflictStrategy.IGNORE)
    suspend fun updateRests(rest: List<Rest>): Int

    @Transaction
    suspend fun upsertRests(rest: List<Rest>) {
        val updateCount = updateRests(rest)
        if (updateCount != rest.size) {
            insertRests(rest)
        }
    }

    //----------------------------------------------------- PRICE TYPE

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceType(priceType: PriceType): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertImageList(image: List<ProductImage>): List<Long>

    @Update(onConflict = OnConflictStrategy.IGNORE)
    suspend fun updateImageList(image: List<ProductImage>): Int

    @Transaction
    suspend fun upsertImageList(image: List<ProductImage>) {
        val updateCount = updateImageList(image)
        if (updateCount != image.size) {
            insertImageList(image)
        }
    }

    @Query("DELETE FROM price_types WHERE db_guid=:id AND timestamp<:time")
    suspend fun deletePriceTypes(id: String, time: Long): Int

    @Query("DELETE FROM product_prices WHERE db_guid=:id AND timestamp<:time")
    suspend fun deletePrices(id: String, time: Long): Int

    @Query("DELETE FROM product_images WHERE db_guid=:id AND timestamp<:time")
    suspend fun deleteImages(id: String, time: Long): Int

    @Query("DELETE FROM products WHERE db_guid=:id AND timestamp<:time")
    suspend fun deleteProducts(id: String, time: Long): Int

    //----------------------------------------------------- CLIENT

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertClientList(clients: List<Client>): List<Long>

    @Update(onConflict = OnConflictStrategy.IGNORE)
    suspend fun updateClientList(clients: List<Client>): Int

    @Transaction
    suspend fun upsertClientList(clients: List<Client>) {
        val updateCount = updateClientList(clients)
        if (updateCount != clients.size) {
            insertClientList(clients)
        }
    }

    //----------------------------------------------------- DEBTS

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDebtList(debt: List<Debt>): List<Long>

    @Update(onConflict = OnConflictStrategy.IGNORE)
    suspend fun updateDebtList(debt: List<Debt>): Int

    @Transaction
    suspend fun upsertDebtList(debt: List<Debt>) {
        val updateCount = updateDebtList(debt)
        if (updateCount != debt.size) {
            insertDebtList(debt)
        }
    }

    //----------------------------------------------------- COMPANY

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompany(company: List<Company>): List<Long>

    @Update(onConflict = OnConflictStrategy.IGNORE)
    suspend fun updateCompany(company: List<Company>): Int

    @Transaction
    suspend fun upsertCompany(company: List<Company>) {
        val updateCount = updateCompany(company)
        if (updateCount != company.size) {
            insertCompany(company)
        }
    }

    //----------------------------------------------------- STORE

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStore(store: List<Store>): List<Long>

    @Update(onConflict = OnConflictStrategy.IGNORE)
    suspend fun updateStore(store: List<Store>): Int

    @Transaction
    suspend fun upsertStore(store: List<Store>) {
        val updateCount = updateStore(store)
        if (updateCount != store.size) {
            insertStore(store)
        }
    }

    //----------------------------------------------------- CLIENT LOCATION

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertClientLocation(loc: List<ClientLocation>): List<Long>

    @Update(onConflict = OnConflictStrategy.IGNORE)
    suspend fun updateClientLocation(loc: List<ClientLocation>): Int

    @Query("UPDATE client_locations SET is_modified=0 WHERE db_guid=:accountGuid AND client_guid=:guid")
    suspend fun updateClientLocation(accountGuid: String, guid: String): Int

    @Transaction
    suspend fun upsertClientLocation(loc: List<ClientLocation>) {
        val updateCount = updateClientLocation(loc)
        if (updateCount != loc.size) {
            insertClientLocation(loc)
        }
    }

    //----------------------------------------------------- PAYMENT TYPE

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPaymentTypes(list: List<PaymentType>): List<Long>

    @Update(onConflict = OnConflictStrategy.IGNORE)
    suspend fun updatePaymentTypes(list: List<PaymentType>): Int

    @Transaction
    suspend fun upsertPaymentTypes(list: List<PaymentType>) {
        val updateCount = updatePaymentTypes(list)
        if (updateCount != list.size) {
            insertPaymentTypes(list)
        }
    }

    @Delete
    suspend fun deleteClientLocation(location: ClientLocation): Int

    @Query("DELETE FROM clients WHERE db_guid=:id AND timestamp<:time")
    suspend fun deleteClients(id: String, time: Long): Int

    @Query("DELETE FROM debts WHERE db_guid=:id AND timestamp<:time")
    suspend fun deleteDebts(id: String, time: Long): Int

    @Query("DELETE FROM payment_types WHERE db_guid=:id AND timestamp<:time")
    suspend fun deletePaymentTypes(id: String, time: Long): Int

    @Query("SELECT * FROM orders WHERE db_guid=:accountGuid AND is_processed=1")
    suspend fun getOrders(accountGuid: String): List<Order>?

    @Query("SELECT * FROM cash WHERE db_guid=:accountGuid AND is_processed=1")
    suspend fun getCash(accountGuid: String): List<Cash>?

    @Query("SELECT " +
            "content._id AS id," +
            "content.order_guid AS orderGuid," +
            "content.product_guid AS productGuid," +
            "IFNULL(product.description, '<?>') AS description," +
            "IFNULL(product.code2, '<?>') AS code," +
            "'' AS groupName," +
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
            "products.code2 " +
            "FROM products WHERE products.db_guid=:accountGuid) AS product " +
            "ON product.guid=content.product_guid " +
            "WHERE content.order_guid=:orderGuid")
    fun getOrderContent(accountGuid: String, orderGuid: String): List<LOrderContent>?

    @Query("UPDATE orders " +
            "SET is_processed=2, is_sent=1, status=:status " +
            "WHERE db_guid=:accountGuid AND guid=:orderGuid")
    suspend fun updateOrder(accountGuid: String, orderGuid: String, status: String): Int

    @Query("UPDATE cash " +
            "SET is_processed=2, is_sent=1, status=:status " +
            "WHERE db_guid=:accountGuid AND guid=:docGuid")
    suspend fun updateCash(accountGuid: String, docGuid: String, status: String): Int

    @Query("UPDATE debts " +
            "SET content=:content " +
            "WHERE db_guid=:accountGuid AND doc_guid=:debtGuid")
    suspend fun updateDebtContent(accountGuid: String, debtGuid: String, content: String)

    @Query("SELECT * FROM client_images WHERE db_guid=:accountGuid AND is_sent=0 AND is_local=1")
    suspend fun getClientImages(accountGuid: String): List<ClientImage>?

    @Query("UPDATE client_images SET is_sent=1, is_local=0, url='' WHERE db_guid=:accountGuid AND guid=:guid")
    suspend fun updateClientImage(accountGuid: String, guid: String): Int

    @Query("SELECT * FROM client_locations WHERE db_guid=:accountGuid AND is_modified=1")
    suspend fun getClientLocations(accountGuid: String): List<ClientLocation>?

    // ========== WebSocket Document Status Updates ==========

    /**
     * Mark order as sent via WebSocket (after receiving ACK from server)
     */
    @Query("UPDATE orders SET is_sent=1 WHERE db_guid=:accountGuid AND guid=:orderGuid")
    suspend fun markOrderSentViaWebSocket(accountGuid: String, orderGuid: String): Int

    /**
     * Mark cash receipt as sent via WebSocket (after receiving ACK from server)
     */
    @Query("UPDATE cash SET is_sent=1 WHERE db_guid=:accountGuid AND guid=:docGuid")
    suspend fun markCashSentViaWebSocket(accountGuid: String, docGuid: String): Int

    /**
     * Mark client image as sent via WebSocket (after receiving ACK from server)
     */
    @Query("UPDATE client_images SET is_sent=1, is_local=0 WHERE db_guid=:accountGuid AND guid=:imageGuid")
    suspend fun markImageSentViaWebSocket(accountGuid: String, imageGuid: String): Int

    /**
     * Mark client location as synced via WebSocket (reset modified flag)
     */
    @Query("UPDATE client_locations SET is_modified=0 WHERE db_guid=:accountGuid AND client_guid=:clientGuid")
    suspend fun markLocationSentViaWebSocket(accountGuid: String, clientGuid: String): Int

}