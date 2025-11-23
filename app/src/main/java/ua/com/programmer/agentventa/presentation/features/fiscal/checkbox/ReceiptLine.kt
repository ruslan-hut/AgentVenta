package ua.com.programmer.agentventa.presentation.features.fiscal.checkbox

import com.google.gson.annotations.SerializedName

data class ReceiptLine(
    @SerializedName("good") val good: Good = Good(),
    @SerializedName("quantity") val quantity: Int = 0,
    @SerializedName("is_return") val isReturn: Boolean = false,
)
