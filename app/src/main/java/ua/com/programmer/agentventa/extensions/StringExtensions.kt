package ua.com.programmer.agentventa.extensions

fun String.asFilter(): String {
    return if (isBlank()) {
        ""
    } else {
        // Lowercase so the filter matches the indexed *_lc columns and is
        // case-insensitive for non-ASCII (Cyrillic) text. SQLite LIKE is only
        // case-insensitive for ASCII, so without this Cyrillic search misses.
        "%${lowercase()}%"
    }
}

fun String.trimForLog(): String {
    return if (length > 8) {
        substring(0, 8) + "..."
    } else {
        this
    }
}

fun String.round(digits: Int): String {
    return try {
        "%.${digits}f".format(this.toDouble()).replace(",", ".")
    } catch (e: Exception) {
        "0.0"
    }
}

fun String.toInt100(): Int {
    return try {
        (this.toDouble() * 100).toInt()
    } catch (e: Exception) {
        0
    }
}

fun String.asNumber(): Int {
    return try {
        toInt()
    } catch (e: Exception) {
        0
    }
}

fun String.fileExtension(): String {
    return try {
        substring(lastIndexOf(".") + 1)
    } catch (e: Exception) {
        ""
    }
}