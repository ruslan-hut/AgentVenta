package ua.com.programmer.agentventa.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.dao.entity.LPrice
import ua.com.programmer.agentventa.dao.entity.LProduct
import ua.com.programmer.agentventa.dao.entity.Product

@Dao
interface ProductDao {

    @Query("SELECT * FROM products " +
            "WHERE " +
            "db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1) " +
            "AND guid=:guid")
    fun getProduct(guid: String): Flow<Product?>

    @Query("""
        SELECT
            product.guid,
            product.code1 AS code,
            product.description,
            product.vendor_code AS vendorCode,
            product.unit,
            'default_unit' AS unitType,
            product.package_value AS packageValue,
            product.package_only AS packageOnly,
            product.indivisible,
            CASE
                WHEN doc_order.price > 0 THEN doc_order.price
                WHEN prices.price > 0 THEN prices.price
                ELSE product.price
            END AS price,
            product.weight,
            product.base_price AS basePrice,
            product.min_price AS minPrice,
            product.is_group AS isGroup,
            product.is_active AS isActive,
            CASE :order WHEN '' THEN 0 ELSE 1 END AS modeSelect,
            product_groups.description AS groupName,
            :type AS priceType,
            IFNULL(doc_order.sum, 0.0) AS sum,
            IFNULL(doc_order.price, 0.0) AS orderPrice,
            IFNULL(doc_order.is_packed, 0) AS isPacked,
            IFNULL(doc_order.is_demand, 0) AS isDemand,
            IFNULL(doc_order.quantity, 0.0) AS quantity,
            IFNULL(images.url, '') AS imageUrl,
            IFNULL(images.guid, '') AS imageGuid
        FROM products AS product
        LEFT OUTER JOIN (
            SELECT product_guid, quantity, price, sum, is_packed, is_demand
            FROM order_content
            WHERE order_guid = :order
        ) AS doc_order ON product.guid = doc_order.product_guid
        LEFT OUTER JOIN (
            SELECT guid, description, db_guid
            FROM products
            WHERE is_group = 1
        ) AS product_groups ON product.group_guid = product_groups.guid AND product.db_guid = product_groups.db_guid
        LEFT OUTER JOIN (
            SELECT product_guid, price_type, price, db_guid
            FROM product_prices
            WHERE price_type = :type
        ) AS prices ON product.guid = prices.product_guid AND product.db_guid = prices.db_guid
        LEFT OUTER JOIN (
            SELECT product_guid, url, guid, db_guid
            FROM product_images
            WHERE isDefault = 1
        ) AS images ON product.guid = images.product_guid AND product.db_guid = images.db_guid
        WHERE
            CASE :filter
                WHEN '' THEN product.group_guid = :group
                ELSE
                    CASE :group
                        WHEN '' THEN product.is_group = 0 AND (product.description LIKE :filter OR product.code1 LIKE :filter)
                        ELSE product.group_guid = :group AND (product.description LIKE :filter OR product.code1 LIKE :filter)
                    END
            END
            AND product.db_guid = :dbGuid
            AND CASE :restOnly
                    WHEN 1 THEN doc_order.quantity > 0 OR product.is_group = 1 OR product.quantity > 0
                    ELSE 1=1
                END
        ORDER BY :sorting
    """)
    fun getProducts(
        dbGuid: String,
        filter: String,
        group: String,
        order: String,
        restOnly: Int,
        sorting: String,
        type: String,
    ): Flow<List<LProduct>>

    @Query("""
        SELECT
            product.guid,
            product.code1 AS code,
            product.description,
            product.vendor_code AS vendorCode,
            product.unit,
            'default_unit' AS unitType,
            IFNULL(rests.quantity, 0.0) AS rest,
            product.package_value AS packageValue,
            product.package_only AS packageOnly,
            product.indivisible,
            CASE
                WHEN doc_order.price > 0 THEN doc_order.price
                WHEN prices.price > 0 THEN prices.price
                ELSE product.price
            END AS price,
            product.weight,
            product.base_price AS basePrice,
            product.min_price AS minPrice,
            product.is_group AS isGroup,
            product.is_active AS isActive,
            CASE :order WHEN '' THEN 0 ELSE 1 END AS modeSelect,
            product_groups.description AS groupName,
            :type AS priceType,
            IFNULL(doc_order.sum, 0.0) AS sum,
            IFNULL(doc_order.price, 0.0) AS orderPrice,
            IFNULL(doc_order.is_packed, 0) AS isPacked,
            IFNULL(doc_order.is_demand, 0) AS isDemand,
            IFNULL(doc_order.quantity, 0.0) AS quantity,
            IFNULL(images.url, '') AS imageUrl,
            IFNULL(images.guid, '') AS imageGuid
        FROM products AS product
        LEFT OUTER JOIN (
            SELECT product_guid, quantity, price, sum, is_packed, is_demand
            FROM order_content
            WHERE order_guid = :order
        ) AS doc_order ON product.guid = doc_order.product_guid
        LEFT OUTER JOIN (
            SELECT guid, description, db_guid
            FROM products
            WHERE is_group = 1
        ) AS product_groups ON product.group_guid = product_groups.guid AND product.db_guid = product_groups.db_guid
        LEFT OUTER JOIN (
            SELECT product_guid, price_type, price, db_guid
            FROM product_prices
            WHERE price_type = :type
        ) AS prices ON product.guid = prices.product_guid AND product.db_guid = prices.db_guid
        LEFT OUTER JOIN (
            SELECT product_guid, quantity, db_guid
            FROM rests
            WHERE company_guid = :company AND store_guid = :store
        ) AS rests ON product.guid = rests.product_guid AND product.db_guid = rests.db_guid
        LEFT OUTER JOIN (
            SELECT product_guid, url, guid, db_guid
            FROM product_images
            WHERE isDefault = 1
        ) AS images ON product.guid = images.product_guid AND product.db_guid = images.db_guid
        WHERE
            CASE :filter
                WHEN '' THEN product.group_guid = :group
                ELSE
                    CASE :group
                        WHEN '' THEN product.is_group = 0 AND (product.description LIKE :filter OR product.code1 LIKE :filter)
                        ELSE product.group_guid = :group AND (product.description LIKE :filter OR product.code1 LIKE :filter)
                    END
            END
            AND product.db_guid = :dbGuid
            AND CASE :restOnly
                    WHEN 1 THEN doc_order.quantity > 0 OR product.is_group = 1 OR
                        product.guid IN (SELECT product_guid FROM rests WHERE quantity>0 AND company_guid=:company AND store_guid=:store)
                    ELSE 1=1
                END
        ORDER BY :sorting
    """)
    fun getProductsWithRests(
        dbGuid: String,
        filter: String,
        group: String,
        order: String,
        restOnly: Int,
        sorting: String,
        type: String,
        company: String,
        store: String,
    ): Flow<List<LProduct>>

    @Query("""
        SELECT
            product.guid,
            product.code1 AS code,
            product.description,
            product.vendor_code AS vendorCode,
            product.unit,
            'default_unit' AS unitType,
            product.quantity AS rest,
            product.package_value AS packageValue,
            product.package_only AS packageOnly,
            product.indivisible,
            CASE
                WHEN doc_order.price > 0 THEN doc_order.price
                WHEN prices.price > 0 THEN prices.price
                ELSE product.price
            END AS price,
            product.weight,
            product.base_price AS basePrice,
            product.min_price AS minPrice,
            product.is_group AS isGroup,
            product.is_active AS isActive,
            CASE :order WHEN '' THEN 0 ELSE 1 END AS modeSelect,
            product_groups.description AS groupName,
            :type AS priceType,
            IFNULL(doc_order.sum, 0.0) AS sum,
            IFNULL(doc_order.price, 0.0) AS orderPrice,
            IFNULL(doc_order.is_packed, 0) AS isPacked,
            IFNULL(doc_order.is_demand, 0) AS isDemand,
            IFNULL(doc_order.quantity, 0.0) AS quantity,
            IFNULL(images.url, '') AS imageUrl,
            IFNULL(images.guid, '') AS imageGuid
        FROM products AS product
        LEFT OUTER JOIN (
            SELECT product_guid, quantity, price, sum, is_packed, is_demand
            FROM order_content
            WHERE order_guid = :order
        ) AS doc_order ON product.guid = doc_order.product_guid
        LEFT OUTER JOIN (
            SELECT guid, description, db_guid
            FROM products
            WHERE is_group = 1
        ) AS product_groups ON product.group_guid = product_groups.guid AND product.db_guid = product_groups.db_guid
        LEFT OUTER JOIN (
            SELECT product_guid, price_type, price, db_guid
            FROM product_prices
            WHERE price_type = :type
        ) AS prices ON product.guid = prices.product_guid AND product.db_guid = prices.db_guid
        LEFT OUTER JOIN (
            SELECT product_guid, url, guid, db_guid
            FROM product_images
            WHERE isDefault = 1
        ) AS images ON product.guid = images.product_guid AND product.db_guid = images.db_guid
        WHERE product.guid = :guid
          AND product.db_guid IN (SELECT guid FROM user_accounts WHERE is_current = 1)
    """)
    fun getProductOrderContent(guid: String, order: String, type: String): Flow<LProduct?>

    @Query("""
        SELECT
            prices.price_type AS priceType,
            prices.price AS price,
            IFNULL(product.base_price, 0.0) AS basePrice,
            IFNULL(types.description, '') AS description,
            CASE WHEN prices.price_type=:currentPriceType THEN 1 ELSE 0 END AS isCurrent
        FROM product_prices AS prices
        LEFT OUTER JOIN (
            SELECT description, price_type, db_guid 
            FROM price_types
        ) AS types ON prices.price_type=types.price_type AND prices.db_guid=types.db_guid
        LEFT OUTER JOIN (
            SELECT guid, base_price, db_guid 
            FROM products 
            WHERE guid=:productGuid
        ) AS product ON prices.product_guid=product.guid AND prices.db_guid=product.db_guid
        WHERE prices.product_guid=:productGuid 
            AND prices.db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1)
    """)
    fun fetchProductPrices(productGuid: String, currentPriceType: String): Flow<List<LPrice>?>

    @Query("""
        SELECT
            product.guid,
            product.code1 AS code,
            product.description,
            product.vendor_code AS vendorCode,
            product.unit,
            'default_unit' AS unitType,
            product.quantity AS rest,
            product.package_value AS packageValue,
            product.package_only AS packageOnly,
            product.indivisible,
            CASE
                WHEN doc_order.price > 0 THEN doc_order.price
                WHEN prices.price > 0 THEN prices.price
                ELSE product.price
            END AS price,
            product.weight,
            product.base_price AS basePrice,
            product.min_price AS minPrice,
            product.is_group AS isGroup,
            product.is_active AS isActive,
            CASE :order WHEN '' THEN 0 ELSE 1 END AS modeSelect,
            product_groups.description AS groupName,
            :type AS priceType,
            IFNULL(doc_order.sum, 0.0) AS sum,
            IFNULL(doc_order.price, 0.0) AS orderPrice,
            IFNULL(doc_order.is_packed, 0) AS isPacked,
            IFNULL(doc_order.is_demand, 0) AS isDemand,
            IFNULL(doc_order.quantity, 0.0) AS quantity,
            IFNULL(images.url, '') AS imageUrl,
            IFNULL(images.guid, '') AS imageGuid
        FROM products AS product
        LEFT OUTER JOIN (
            SELECT product_guid, quantity, price, sum, is_packed, is_demand
            FROM order_content WHERE order_guid=:order
        ) AS doc_order ON product.guid=doc_order.product_guid
        LEFT OUTER JOIN (SELECT guid, description, db_guid FROM products WHERE is_group=1) AS product_groups
            ON product.group_guid=product_groups.guid AND product.db_guid=product_groups.db_guid
        LEFT OUTER JOIN (SELECT product_guid, price_type, price, db_guid FROM product_prices WHERE price_type=:type) AS prices
            ON product.guid=prices.product_guid AND product.db_guid=prices.db_guid
        LEFT OUTER JOIN (SELECT product_guid, url, guid, db_guid FROM product_images WHERE isDefault=1) AS images
            ON product.guid=images.product_guid AND product.db_guid=images.db_guid
        WHERE product.barcode LIKE :barcode AND product.db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1)
    """)
    suspend fun getProductByBarcode(barcode: String, order: String, type: String): LProduct?
}