package ua.com.programmer.agentventa.extensions

import android.view.View

fun View.visibleIf(condition: Boolean) {
    visibility = if (condition) View.VISIBLE else View.GONE
}

fun View.visibleOrInvisibleIf(condition: Boolean) {
    visibility = if (condition) View.VISIBLE else View.INVISIBLE
}
