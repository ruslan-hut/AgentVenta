package ua.com.programmer.agentventa.dao.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import ua.com.programmer.agentventa.utility.XMap

@Entity(tableName = "debts", primaryKeys = ["db_guid","client_guid","doc_id"])
data class Debt(
    @ColumnInfo(name = "db_guid") val databaseId: String = "",
    @ColumnInfo(name = "client_guid") val clientGuid: String = "",
    @ColumnInfo(name = "doc_guid") val docGuid: String = "",
    @ColumnInfo(name = "doc_id") val docId: String = "",
    @ColumnInfo(name = "doc_type") val docType: String = "",
    @ColumnInfo(name = "has_content") val hasContent: Int = 0,
    val content: String = "",
    val sum: Double = 0.0,
    @ColumnInfo(name = "sum_in") val sumIn: Double = 0.0,
    @ColumnInfo(name = "sum_out") val sumOut: Double = 0.0,
    @ColumnInfo(name = "is_total") val isTotal: Int = 0,
    val sorting: Int = 0,
    val timestamp: Long = 0
){
    companion object Builder {
        fun build(data: XMap): Debt {
            val docId = data.getString("doc_id")
            return Debt(
                databaseId = data.getDatabaseId(),
                clientGuid = data.getString("client_guid"),
                docGuid = data.getString("doc_guid"),
                docId = docId,
                docType = data.getString("doc_type"),
                hasContent = data.getInt("has_content"),
                content = data.getString("content"),
                sum = data.getDouble("sum"),
                sumIn = data.getDouble("sum_in"),
                sumOut = data.getDouble("sum_out"),
                isTotal = if (docId.isBlank()) 1 else 0,
                sorting = data.getInt("sorting"),
                timestamp = data.getTimestamp()
            )
        }
    }
}

fun Debt.isValid(): Boolean {
    return databaseId.isNotEmpty() && clientGuid.isNotEmpty()
}