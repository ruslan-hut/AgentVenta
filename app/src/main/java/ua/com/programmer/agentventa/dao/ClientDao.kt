package ua.com.programmer.agentventa.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.dao.entity.Client
import ua.com.programmer.agentventa.dao.entity.ClientImage
import ua.com.programmer.agentventa.dao.entity.Debt
import ua.com.programmer.agentventa.dao.entity.LClient
import ua.com.programmer.agentventa.dao.entity.LClientLocation

@Dao
interface ClientDao {

    @Query("""
        SELECT * FROM clients
            WHERE
            db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1)
            AND guid=:guid
    """)
    fun getClient(guid: String): Flow<Client?>

    @Query("""
        SELECT
            clients.guid AS guid,
            clients.timestamp AS timestamp,
            clients.code1 AS code,
            clients.description AS description,
            clients.notes AS notes,
            clients.phone AS phone,
            clients.address AS address,
            clients.discount AS discount,
            clients.bonus AS bonus,
            clients.price_type AS priceType,
            clients.is_banned AS isBanned,
            clients.ban_message AS banMessage,
            clients.group_guid AS groupGuid,
            clients.is_active AS isActive,
            clients.is_group AS isGroup,
            IFNULL(debts.sum, 0.0) AS debt,
            IFNULL(location.latitude, 0.0) AS latitude,
            IFNULL(location.longitude, 0.0) AS longitude,
            IFNULL(client_groups.description, '') AS groupName
        FROM clients
        LEFT OUTER JOIN (SELECT guid, description, db_guid FROM clients WHERE is_group=1) AS client_groups
        ON clients.group_guid=client_groups.guid AND clients.db_guid=client_groups.db_guid
        LEFT OUTER JOIN (SELECT client_guid, db_guid, sum FROM debts WHERE is_total=1 AND company_guid=:company) AS debts
        ON clients.guid=debts.client_guid AND clients.db_guid=debts.db_guid
        LEFT OUTER JOIN (SELECT * FROM client_locations) AS location
        ON clients.guid=location.client_guid AND clients.db_guid=location.db_guid
        WHERE
        CASE :filter WHEN '' THEN clients.group_guid=:group
        ELSE CASE :group WHEN '' THEN is_group=0
        AND (clients.description LIKE :filter OR clients.code1 LIKE :filter OR clients.phone LIKE :filter)
        ELSE clients.group_guid=:group
        AND (clients.description LIKE :filter OR clients.code1 LIKE :filter OR clients.phone LIKE :filter) END END
        AND clients.db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1)
        ORDER BY clients.is_group DESC, clients.description
    """)
    fun getClients(group: String, filter: String, company: String): Flow<List<LClient>>

    @Query("""
        SELECT
            clients.guid AS guid,
            clients.timestamp AS timestamp,
            clients.code1 AS code,
            clients.description AS description,
            clients.notes AS notes,
            clients.phone AS phone,
            clients.address AS address,
            clients.discount AS discount,
            clients.bonus AS bonus,
            clients.price_type AS priceType,
            clients.is_banned AS isBanned,
            clients.ban_message AS banMessage,
            clients.group_guid AS groupGuid,
            clients.is_active AS isActive,
            clients.is_group AS isGroup,
            IFNULL(location.latitude, 0.0) AS latitude,
            IFNULL(location.longitude, 0.0) AS longitude,
            IFNULL(debts.sum, 0.0) AS debt,
            IFNULL(client_groups.description, '') AS groupName
        FROM clients
        LEFT OUTER JOIN (SELECT guid, description, db_guid FROM clients WHERE is_group=1) AS client_groups
        ON clients.group_guid=client_groups.guid AND clients.db_guid=client_groups.db_guid
        LEFT OUTER JOIN (SELECT client_guid, db_guid, sum FROM debts WHERE is_total=1 AND company_guid=:companyGuid) AS debts
        ON clients.guid=debts.client_guid AND clients.db_guid=debts.db_guid
        LEFT OUTER JOIN (SELECT * FROM client_locations) AS location
        ON clients.guid=location.client_guid AND clients.db_guid=location.db_guid
        WHERE
        clients.guid=:guid AND clients.db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1)
    """)
    fun getClientInfo(guid: String, companyGuid: String): Flow<LClient?>

    @Query(""" 
            SELECT * FROM debts
            WHERE client_guid=:guid
            AND is_total=0
            AND company_guid=:companyGuid
            AND db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1)
            ORDER BY sorting
    """)
    fun getClientDebts(guid: String, companyGuid: String): Flow<List<Debt>>

    @Query("SELECT * FROM debts " +
            "WHERE client_guid=:guid AND doc_id=:docId " +
            "AND is_total=0 " +
            "AND db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1) " +
            "ORDER BY sorting")
    fun getClientDebt(guid: String, docId: String): Flow<Debt?>

    @Query("SELECT " +
            "client_locations.db_guid AS databaseId," +
            "client_locations.client_guid AS clientGuid," +
            "client_locations.latitude," +
            "client_locations.longitude," +
            "IFNULL(client.description, '') AS description," +
            "CASE TRIM(IFNULL(client.address, '')) WHEN '' THEN client_locations.address ELSE client.address END AS address " +
            "FROM client_locations " +
            "LEFT OUTER JOIN (SELECT guid, db_guid, address, description  FROM clients WHERE guid=:guid) AS client " +
            "ON client_locations.client_guid=client.guid AND client_locations.db_guid=client.db_guid " +
            "WHERE client_locations.client_guid=:guid " +
            "AND client_locations.db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1)")
    fun getClientLocation(guid: String): Flow<LClientLocation?>

    @Query("SELECT " +
            "client_locations.db_guid AS databaseId," +
            "client_locations.client_guid AS clientGuid," +
            "client_locations.latitude," +
            "client_locations.longitude," +
            "IFNULL(client.description, '') AS description," +
            "CASE TRIM(IFNULL(client.address, '')) WHEN '' THEN client_locations.address ELSE client.address END AS address " +
            "FROM client_locations " +
            "LEFT OUTER JOIN (SELECT guid, db_guid, address, description  FROM clients) AS client " +
            "ON client_locations.client_guid=client.guid AND client_locations.db_guid=client.db_guid " +
            "AND client_locations.db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1)")
    fun getClientLocations(): Flow<List<LClientLocation>?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertClientImage(image: ClientImage): Long

    @Update(onConflict = OnConflictStrategy.IGNORE)
    suspend fun updateClientImage(image: ClientImage): Int

    @Transaction
    suspend fun upsertClientImage(image: ClientImage) {
        val updateCount = updateClientImage(image)
        if (updateCount == 0) {
            insertClientImage(image)
        }
    }

    @Query("SELECT * FROM client_images " +
        "WHERE client_guid=:guid " +
        "AND db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1) " +
    "UNION ALL " +
    "SELECT " +
        "db_guid," +
        "product_guid," +
        "guid," +
        "url," +
        "description," +
        "timestamp," +
        "0," +
        "0," +
        "isDefault " +
        "FROM product_images " +
        "WHERE product_guid=:guid AND type='client' " +
            "AND guid NOT IN " +
            "(SELECT guid FROM client_images " +
            "WHERE db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1))" +
        "AND db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1) " +
    "ORDER BY is_default DESC, timestamp DESC ")
    fun getClientImages(guid: String): Flow<List<ClientImage>>

    @Query("SELECT * FROM client_images " +
            "WHERE guid=:imageGuid " +
            "AND db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1) " +
            "UNION ALL " +
            "SELECT " +
            "db_guid," +
            "product_guid," +
            "guid," +
            "url," +
            "description," +
            "timestamp," +
            "0," +
            "0," +
            "isDefault " +
            "FROM product_images " +
            "WHERE guid=:imageGuid AND type='client' " +
            "AND guid NOT IN " +
            "(SELECT guid FROM client_images " +
            "WHERE db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1))" +
            "AND db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1)")
    fun getClientImage(imageGuid: String): Flow<ClientImage>

    @Query("DELETE FROM client_images " +
            "WHERE guid=:imageGuid " +
            "AND db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1)")
    suspend fun deleteClientImage(imageGuid: String)

    @Query("UPDATE client_images SET is_default=1 " +
            "WHERE guid=:imageGuid " +
            "AND db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1)")
    suspend fun setAsDefault(imageGuid: String)

    @Query("UPDATE client_images SET is_default=0 " +
            "WHERE client_guid=:clientGuid " +
            "AND db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1)")
    suspend fun resetDefault(clientGuid: String)
}