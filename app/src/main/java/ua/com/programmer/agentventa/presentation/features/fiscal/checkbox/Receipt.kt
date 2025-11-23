package ua.com.programmer.agentventa.presentation.features.fiscal.checkbox

import com.google.gson.annotations.SerializedName

data class Receipt(
    @SerializedName("id") val id: String = "",
    @SerializedName("goods") val goods: List<ReceiptLine> = listOf(),
    @SerializedName("payments") val payments: List<Payment> = listOf(),
)
