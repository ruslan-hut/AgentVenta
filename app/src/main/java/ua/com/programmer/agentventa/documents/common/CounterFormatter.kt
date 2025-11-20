package ua.com.programmer.agentventa.documents.common

import ua.com.programmer.agentventa.dao.entity.DocumentTotals
import java.util.Locale

/**
 * Interface for formatting document counters and totals.
 * Follows Open/Closed Principle - open for extension, closed for modification.
 */
interface CounterFormatter {
    fun formatDocumentsCount(count: Int): String
    fun formatReturnsCount(count: Int): String
    fun formatWeight(weight: Double): String
    fun formatSum(sum: Double): String
}

/**
 * Default implementation using locale-specific number formatting.
 */
class DefaultCounterFormatter : CounterFormatter {

    override fun formatDocumentsCount(count: Int): String {
        return String.format(Locale.getDefault(), "%d", count)
    }

    override fun formatReturnsCount(count: Int): String {
        return String.format(Locale.getDefault(), "%d", count)
    }

    override fun formatWeight(weight: Double): String {
        return String.format(Locale.getDefault(), "%.3f", weight)
    }

    override fun formatSum(sum: Double): String {
        return String.format(Locale.getDefault(), "%.2f", sum)
    }
}

/**
 * Data class containing formatted counter values.
 */
data class FormattedCounters(
    val documentsCount: String,
    val returnsCount: String,
    val totalWeight: String,
    val totalSum: String,
    val hasData: Boolean
)

/**
 * Extension function to format DocumentTotals using a CounterFormatter.
 */
fun DocumentTotals.format(formatter: CounterFormatter): FormattedCounters {
    return FormattedCounters(
        documentsCount = formatter.formatDocumentsCount(documents),
        returnsCount = formatter.formatReturnsCount(returns),
        totalWeight = formatter.formatWeight(weight),
        totalSum = formatter.formatSum(sum),
        hasData = documents > 0 || returns > 0
    )
}
