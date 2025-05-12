package ua.com.programmer.agentventa.dao.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Locale

@Entity(tableName = "cash")
data class Cash(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "_id") val id: Int = 0,
    @ColumnInfo(name = "db_guid") var databaseId: String = "",
    val date: String = "",
    val time: Long = 0,
    val number: Int = 0,
    val guid: String = "",
    @ColumnInfo(name = "company_guid") val companyGuid: String = "",
    @ColumnInfo(name = "client_guid") val clientGuid: String = "",
    @ColumnInfo(name = "reference_guid") val referenceGuid: String = "",
    val sum: Double = 0.0,
    val notes: String = "",
    @ColumnInfo(name = "fiscal_number") val fiscalNumber: Int = 0,
    @ColumnInfo(name = "is_fiscal") val isFiscal: Int = 0,
    @ColumnInfo(name = "is_processed") val isProcessed: Int = 0,
    @ColumnInfo(name = "is_sent") val isSent: Int = 0
)

fun Cash.getSumFormatted(): String = String.format(Locale.getDefault(),"%.2f", sum)
