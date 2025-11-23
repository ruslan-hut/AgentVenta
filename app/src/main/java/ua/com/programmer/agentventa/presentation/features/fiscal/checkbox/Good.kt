package ua.com.programmer.agentventa.presentation.features.fiscal.checkbox

import com.google.gson.annotations.SerializedName

data class Good(
    @SerializedName("code") val code: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("price") val price: Int = 0,
)
