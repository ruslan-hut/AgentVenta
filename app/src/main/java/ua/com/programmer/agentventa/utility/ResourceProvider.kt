package ua.com.programmer.agentventa.utility

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * Interface for accessing string resources in ViewModels and other non-UI classes.
 * This abstraction enables:
 * - Testability (can be mocked in unit tests)
 * - Clean architecture (no direct Android Context dependency)
 * - Consistent localization across all layers
 */
interface ResourceProvider {

    /**
     * Returns a localized string from the application's package's default string table.
     * @param resId Resource id for the string
     */
    fun getString(@StringRes resId: Int): String

    /**
     * Returns a localized formatted string from the application's package's default string table,
     * substituting the format arguments as defined in Formatter and String.format(String, Object...).
     * @param resId Resource id for the format string
     * @param formatArgs The format arguments that will be used for substitution
     */
    fun getString(@StringRes resId: Int, vararg formatArgs: Any): String

    /**
     * Returns a localized plural string from the application's package's default string table.
     * @param resId Resource id for the plurals resource
     * @param quantity The number used to get the correct string for the current language's plural rules
     */
    fun getQuantityString(@PluralsRes resId: Int, quantity: Int): String

    /**
     * Returns a localized formatted plural string from the application's package's default string table.
     * @param resId Resource id for the plurals resource
     * @param quantity The number used to get the correct string for the current language's plural rules
     * @param formatArgs The format arguments that will be used for substitution
     */
    fun getQuantityString(@PluralsRes resId: Int, quantity: Int, vararg formatArgs: Any): String
}
