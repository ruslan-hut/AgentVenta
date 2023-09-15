package ua.com.programmer.agentventa.dao.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "log_events")
data class LogEvent(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val timestamp: Long,
    val level: String,
    val tag: String,
    val message: String
)
