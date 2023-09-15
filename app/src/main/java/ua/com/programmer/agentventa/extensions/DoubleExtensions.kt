package ua.com.programmer.agentventa.extensions

fun Double.format(digits: Int, ifNull: String = "", append: String = ""): String {
    return if (this == 0.0) ifNull
    else "%.${digits}f".format(this).replace(",",".").plus(" $append")
}

fun Double.round(digits: Int): Double {
    return try {
        "%.${digits}f".format(this).replace(",", ".").toDouble()
    } catch (e: Exception) {
        0.0
    }
}

fun Double.formatAsInt(digits: Int, ifNull: String = "", append: String = ""): String {
    if (this == 0.0) return ifNull
    return if (this % 1 == 0.0) String.format("%.0f", this).plus(" $append")
    else this.format(digits, ifNull, append)
}