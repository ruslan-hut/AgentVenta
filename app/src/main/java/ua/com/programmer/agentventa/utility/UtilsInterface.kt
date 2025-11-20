package ua.com.programmer.agentventa.utility

/**
 * Interface for utility functions to support dependency inversion principle.
 * Allows mocking in tests and provides better abstraction.
 */
interface UtilsInterface {

    // Numeric formatting and rounding
    fun round(i: Double, accuracy: Int): Double
    fun round(i: String, accuracy: Int): Double
    fun format(i: String, accuracy: Int): String
    fun format(i: Double, accuracy: Int): String
    fun formatAsInteger(i: Double, accuracy: Int): String
    fun formatDistance(distance: Double): String
    fun formatWeight(weight: Double): String

    // Type conversion
    fun getInteger(i: String): Int
    fun getLong(i: String): Long
    fun getString(s: String): String

    // Date and time operations
    fun currentTime(): Long
    fun showTime(begin: Long, end: Long): String
    fun dateBeginOfToday(): Long
    fun dateBeginOfDay(time: Long): Long
    fun dateEndOfToday(): Long
    fun dateBeginShiftDate(numberOfDays: Int): Long
    fun dateEndShiftDate(numberOfDays: Int): Long
    fun dateLocal(time: Long): String
    fun dateLocalShort(time: Long): String

    // Logging
    fun log(logType: String, message: String)
    fun warn(message: String)
    fun error(message: String)
    fun debug(message: String)
    fun readLogs(): StringBuilder

    // UI helpers
    fun getPageTitleID(tag: String): Int

    // Math operations
    fun max(arg1: Double, arg2: Double): Double
}
