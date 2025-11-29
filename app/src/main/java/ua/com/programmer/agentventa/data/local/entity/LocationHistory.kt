package ua.com.programmer.agentventa.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "locations")
data class LocationHistory(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "_id") val id: Int = 0,
    val time: Long = 0,
    val accuracy: Double = 0.0,
    val altitude: Double = 0.0,
    val bearing: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val provider: String = "",
    val speed: Double = 0.0,
    val distance: Double = 0.0,
    @ColumnInfo(name = "point_name") val pointName: String = "",
    val extra: String = ""
)

fun LocationHistory.interval(currentTime: Long): Long {
    return currentTime - time / 1000
}

fun LocationHistory.toMap(): Map<String, Any> {
    return mapOf(
        "id" to id,
        "time" to time,
        "accuracy" to accuracy,
        "altitude" to altitude,
        "bearing" to bearing,
        "latitude" to latitude,
        "longitude" to longitude,
        "provider" to provider,
        "speed" to speed,
        "distance" to distance,
        "point_name" to pointName,
        "extra" to extra
    )
}