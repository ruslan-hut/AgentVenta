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
            db_guid = :currentDbGuid
            AND guid=:guid
    """)
    fun getClient(currentDbGuid: String, guid: String): Flow<Client?>

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
        AND clients.db_guid = :currentDbGuid
        ORDER BY clients.is_group DESC, clients.description
    """)
    fun getClients(currentDbGuid: String, group: String, filter: String, company: String): Flow<List<LClient>>

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
        clients.guid=:guid AND clients.db_guid = :currentDbGuid
    """)
    fun getClientInfo(currentDbGuid: String, guid: String, companyGuid: String): Flow<LClient?>

    @Query("""
            SELECT * FROM debts
            WHERE client_guid=:guid
            AND is_total=0
            AND company_guid=:companyGuid
            AND db_guid = :currentDbGuid
            ORDER BY sorting
    """)
    fun getClientDebts(currentDbGuid: String, guid: String, companyGuid: String): Flow<List<Debt>>

    @Query("""
        SELECT * FROM debts
            WHERE client_guid=:guid AND doc_id=:docId
            AND is_total=0
            AND db_guid = :currentDbGuid
            ORDER BY sorting
    """)
    fun getClientDebt(currentDbGuid: String, guid: String, docId: String): Flow<Debt?>

    @Query("""
        SELECT
            cl.db_guid AS databaseId,
            cl.client_guid AS clientGuid,
            cl.latitude,
            cl.longitude,
            IFNULL(c.description, '') AS description,
            CASE TRIM(IFNULL(c.address, '')) WHEN '' THEN cl.address ELSE c.address END AS address
        FROM client_locations cl
        LEFT OUTER JOIN clients c ON cl.client_guid = c.guid AND cl.db_guid = c.db_guid
        WHERE cl.client_guid = :guid
        AND cl.db_guid = :currentDbGuid
    """)
    fun getClientLocation(currentDbGuid: String, guid: String): Flow<LClientLocation?>

    @Query("""
        SELECT
            cl.db_guid AS databaseId,
            cl.client_guid AS clientGuid,
            cl.latitude,
            cl.longitude,
            IFNULL(c.description, '') AS description,
            CASE TRIM(IFNULL(c.address, '')) WHEN '' THEN cl.address ELSE c.address END AS address
        FROM client_locations cl
        LEFT OUTER JOIN clients c ON cl.client_guid = c.guid AND cl.db_guid = c.db_guid
        WHERE cl.db_guid = :currentDbGuid
    """)
    fun getClientLocations(currentDbGuid: String): Flow<List<LClientLocation>?>

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

    @Query("""
        SELECT * FROM client_images
        WHERE client_guid=:guid
        AND db_guid = :currentDbGuid
    UNION ALL
    SELECT
        db_guid,
        product_guid,
        guid,
        url,
        description,
        timestamp,
        0,
        0,
        isDefault
        FROM product_images
        WHERE product_guid=:guid AND type='client'
            AND guid NOT IN
            (SELECT guid FROM client_images
            WHERE db_guid = :currentDbGuid)
        AND db_guid = :currentDbGuid
    ORDER BY is_default DESC, timestamp DESC
    """)
    fun getClientImages(currentDbGuid: String, guid: String): Flow<List<ClientImage>>

    @Query("""
        SELECT * FROM client_images
        WHERE guid=:imageGuid
        AND db_guid = :currentDbGuid
    UNION ALL
    SELECT
        db_guid,
        product_guid,
        guid,
        url,
        description,
        timestamp,
        0,
        0,
        isDefault
        FROM product_images
        WHERE guid=:imageGuid AND type='client'
            AND guid NOT IN
            (SELECT guid FROM client_images
            WHERE db_guid = :currentDbGuid)
        AND db_guid = :currentDbGuid
    """)
    fun getClientImage(currentDbGuid: String, imageGuid: String): Flow<ClientImage>

    @Query("""DELETE FROM client_images
            WHERE guid=:imageGuid
            AND db_guid = :currentDbGuid""")
    suspend fun deleteClientImage(currentDbGuid: String, imageGuid: String)

    @Query("""
        UPDATE client_images SET is_default=1
            WHERE guid=:imageGuid
            AND db_guid = :currentDbGuid
    """)
    suspend fun setAsDefault(currentDbGuid: String, imageGuid: String)

    @Query("""
        UPDATE client_images SET is_default=0
            WHERE client_guid=:clientGuid
            AND db_guid = :currentDbGuid
    """)
    suspend fun resetDefault(currentDbGuid: String, clientGuid: String)

    @Transaction
    suspend fun makeImageDefault(currentDbGuid: String, clientGuid: String, imageGuid: String) {
        resetDefault(currentDbGuid, clientGuid)
        setAsDefault(currentDbGuid, imageGuid)
    }
}