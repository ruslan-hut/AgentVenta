package ua.com.programmer.agentventa.fiscal.checkbox

import com.google.gson.annotations.SerializedName

data class Payment(
    @SerializedName("type") val type: String = "",
    @SerializedName("value") val value: Int = 0,
)
