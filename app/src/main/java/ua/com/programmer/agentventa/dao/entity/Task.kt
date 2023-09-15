package ua.com.programmer.agentventa.dao.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "tasks", primaryKeys = ["db_guid","guid"])
data class Task(
    @ColumnInfo(name = "db_guid") val databaseId: String = "",
    val guid: String,
    val time: Long,
    @ColumnInfo(name = "is_done") val isDone: Int = 0,
    val color: String = "",
    val date: String = "",
    @ColumnInfo(name = "client_guid") val clientGuid: String = "",
    val description: String = "",
    val notes: String = "",
)
