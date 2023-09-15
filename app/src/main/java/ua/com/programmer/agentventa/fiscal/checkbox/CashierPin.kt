package ua.com.programmer.agentventa.fiscal.checkbox

import com.google.gson.annotations.SerializedName

data class CashierPin(
    @SerializedName("pin_code") val pin: String = "",
)
