package ua.com.programmer.agentventa.presentation.features.fiscal.checkbox

import com.google.gson.annotations.SerializedName

data class ServiceReceipt(
    @SerializedName("id") val id: String = "",
    @SerializedName("payment") val payment: Payment = Payment(),
)
