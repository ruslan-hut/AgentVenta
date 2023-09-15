package ua.com.programmer.agentventa.http

data class SendResult(
    val account: String,
    val type: String,
    val guid: String,
    val status: String,
    val error: String,
)
