package ua.com.programmer.agentventa.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import ua.com.programmer.agentventa.dao.entity.Client
import ua.com.programmer.agentventa.dao.entity.ClientImage
import ua.com.programmer.agentventa.dao.entity.ClientLocation
import ua.com.programmer.agentventa.dao.entity.Debt
import ua.com.programmer.agentventa.dao.entity.LOrderContent
import ua.com.programmer.agentventa.dao.entity.Order
import ua.com.programmer.agentventa.dao.entity.PaymentType
import ua.com.programmer.agentventa.dao.entity.PriceType
import ua.com.programmer.agentventa.dao.entity.Product
import ua.com.programmer.agentventa.dao.entity.ProductImage
import ua.com.programmer.agentventa.dao.entity.ProductPrice

@Dao
interface DataExchangeDao {

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

    @Query("SELECT " +
            "content._id AS id," +
            "content.order_guid AS orderGuid," +
            "content.product_guid AS productGuid," +
            "product.description AS description," +
            "product.code2 AS code," +
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

}