package ua.com.programmer.agentventa.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "debug_log_entries",
    indices = [
        Index(value = ["sent"]),
        Index(value = ["timestamp"])
    ]
)
data class DebugLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val level: String,
    val tag: String,
    val message: String,
    val fields: String = "{}",
    val sent: Int = 0,
    val attempts: Int = 0
)
