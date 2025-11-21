package ua.com.programmer.agentventa.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.dao.entity.ClientLocation
import ua.com.programmer.agentventa.dao.entity.LocationHistory

@Dao
interface LocationDao {

    @Insert
    suspend fun insertLocation(location: LocationHistory): Long

    @Query("SELECT * FROM locations ORDER BY time DESC LIMIT 1")
    suspend fun getLastLocation(): LocationHistory?

    @Query("SELECT * FROM locations ORDER BY time DESC LIMIT 1")
    fun currentLocation(): Flow<LocationHistory?>

    @Query("SELECT " +
            "client_locations.db_guid," +
            "client_locations.client_guid," +
            "client_locations.latitude," +
            "client_locations.longitude," +
            "client_locations.is_modified," +
            "CASE TRIM(client.address) WHEN '' THEN client_locations.address ELSE client.address END AS address " +
            "FROM client_locations " +
            "LEFT OUTER JOIN (SELECT guid, db_guid, address  FROM clients WHERE guid=:guid) AS client " +
            "ON client_locations.client_guid=client.guid AND client_locations.db_guid=client.db_guid " +
            "WHERE client_locations.client_guid=:guid " +
            "AND client_locations.db_guid = :currentDbGuid")
    suspend fun getClientLocation(currentDbGuid: String, guid: String): ClientLocation?

}