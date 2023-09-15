package ua.com.programmer.agentventa.extensions

import java.util.Date
import java.util.Locale

fun Date.beginOfDay(): Long {
    val calendar = java.util.Calendar.getInstance().apply {
        time = this@beginOfDay
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    return calendar.timeInMillis / 1000
}

fun Date.endOfDay(): Long {
    val calendar = java.util.Calendar.getInstance().apply {
        time = this@endOfDay
        set(java.util.Calendar.HOUR_OF_DAY, 23)
        set(java.util.Calendar.MINUTE, 59)
        set(java.util.Calendar.SECOND, 59)
        set(java.util.Calendar.MILLISECOND, 999)
    }
    return calendar.timeInMillis / 1000
}

fun Date.localFormatted(): String {
    return String.format(Locale.getDefault(), "%1\$td-%1\$tm-%1\$tY", this)
}