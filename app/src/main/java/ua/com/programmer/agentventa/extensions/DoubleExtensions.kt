package ua.com.programmer.agentventa.extensions

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Formats a Double to a specified number of decimal places and appends an optional string.
 *
 * @param digits The number of decimal places to format to.
 * @param ifNull The string to return if the Double is 0.0.
 * @param append The string to append to the end of the formatted number.
 * @return The formatted String. Returns the value of 'ifNull' if the Double is 0.0.
 */

fun Double.format(digits: Int, ifNull: String = "", append: String = ""): String {
    return if (this == 0.0) ifNull
    else "%.${digits}f".format(this.round(digits)).replace(",",".").plus(" $append")
}

/**
 * Rounds a Double to a specified number of decimal places.
 *
 * @param digits The number of decimal places to round to.
 * @return The rounded Double value. Returns 0.0 if any exception occurs during the operation.
 */

fun Double.round(digits: Int): Double {
    return try {
        BigDecimal(this).setScale(digits, RoundingMode.HALF_UP).toDouble()
    } catch (e: Exception) {
        0.0
    }
}

/**
 * Rounds a Double to the nearest Int after multiplying it by a given factor.
 *
 * @param multi The factor to multiply the Double by before rounding.
 * @return The rounded Int value. Returns 0 if any exception occurs during the operation.
 *
 * Usage:
 *  val result = 12.34.roundToInt(100)  // Result will be 1234
 */
fun Double.roundToInt(multi: Int): Int {
    val number = this * multi
    return try {
        BigDecimal(number).setScale(0, RoundingMode.HALF_UP).toInt()
    } catch (e: Exception) {
        0
    }
}

/**
 * Formats a Double as an integer if it has no decimal part, otherwise formats it to a specified number of decimal places.
 *
 * @param digits The number of decimal places to format to if the number is not an integer.
 * @param ifNull The string to return if the Double is 0.0.
 * @param append The string to append to the end of the formatted number.
 * @return The formatted String. Returns the value of 'ifNull' if the Double is 0.0.
 */

fun Double.formatAsInt(digits: Int, ifNull: String = "", append: String = ""): String {
    if (this == 0.0) return ifNull
    return if (this % 1 == 0.0) String.format("%.0f", this).plus(" $append")
    else this.format(digits, ifNull, append)
}