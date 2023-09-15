package ua.com.programmer.agentventa.fiscal

data class FiscalState(
    val authorized: Boolean = false,
    val offline: Boolean = false,
    val shiftOpened: Boolean = false,
    val shiftOpenedAt: String = "",
)
