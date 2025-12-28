package ua.com.programmer.agentventa.infrastructure.printer

import ua.com.programmer.agentventa.data.local.entity.LOrderContent
import ua.com.programmer.agentventa.data.local.entity.Order
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Formats order data as text for thermal printer output.
 * Uses two-line product layout:
 * - Line 1: quantity × price (right-aligned)
 * - Line 2: product name + sum (right-aligned)
 */
@Singleton
class OrderPrintFormatter @Inject constructor() {

    /**
     * Format complete order as printable text.
     *
     * @param order Order header data
     * @param content Order content lines
     * @param width Print area width in characters (default 32)
     * @param companyName Company name to display (optional)
     * @param deviceId Device identifier (show last 6 chars)
     * @return Formatted text ready for printing
     */
    fun format(
        order: Order,
        content: List<LOrderContent>,
        width: Int = 32,
        companyName: String = "",
        deviceId: String = ""
    ): String {
        val sb = StringBuilder()
        val separator = "-".repeat(width)

        // Header: Company name (centered)
        if (companyName.isNotBlank()) {
            sb.appendLine(centerText(companyName, width))
        }

        // Title (centered)
        val title = if (order.isReturn == 1) "ПОВЕРНЕННЯ" else "ВИДАТКОВИЙ ЧЕК"
        sb.appendLine(centerText(title, width))
        sb.appendLine()

        // Date and order number
        val numberText = order.number.toString().padStart(6, '0')
        val dateLine = formatTwoColumns(order.date, numberText, width)
        sb.appendLine(dateLine)

        // Separator
        sb.appendLine(separator)

        // Content lines (multi-line format per product)
        for (item in content) {
            // Line 1: quantity × price
            val qtyText = formatQuantity(item.quantity)
            val priceText = formatPrice(item.price)
            val line1 = "$qtyText x ${" ".repeat(width - qtyText.length - priceText.length - 3)}$priceText"
            sb.appendLine(line1)

            // Product name lines + sum on last line
            val sumText = formatPrice(item.sum)
            val productName = item.description

            // Check if name fits in one line with sum
            val maxNameLengthWithSum = width - sumText.length - 1
            if (productName.length <= maxNameLengthWithSum) {
                // Single line: name + sum
                val line = formatTwoColumns(productName, sumText, width)
                sb.appendLine(line)
            } else {
                // Multiple lines: wrap name, put sum on last line
                val nameLines = wrapText(productName, width)
                for (i in nameLines.indices) {
                    if (i == nameLines.lastIndex) {
                        // Last line: name part + sum (right-aligned)
                        val lastLineName = nameLines[i]
                        if (lastLineName.length <= maxNameLengthWithSum) {
                            sb.appendLine(formatTwoColumns(lastLineName, sumText, width))
                        } else {
                            // Name too long even for last line, print name then sum on new line
                            sb.appendLine(lastLineName)
                            sb.appendLine(" ".repeat(width - sumText.length) + sumText)
                        }
                    } else {
                        // Not last line: just the name part
                        sb.appendLine(nameLines[i])
                    }
                }
            }
        }

        // Separator
        sb.appendLine(separator)

        // Total
        val totalLabel = "РАЗОМ:"
        val totalValue = formatPrice(order.price)
        sb.appendLine(formatTwoColumns(totalLabel, totalValue, width))

        // Discount (if present)
        if (order.discountValue > 0) {
            val discountLabel = "Знижка:"
            val discountValue = formatPrice(order.discountValue)
            sb.appendLine(formatTwoColumns(discountLabel, discountValue, width))
        }

        // Store info (if present)
        if (order.store.isNotBlank()) {
            sb.appendLine()
            sb.appendLine(truncateText(order.store, width))
        }

        // Notes (if present)
        if (order.notes.isNotBlank()) {
            sb.appendLine()
            val notesLines = wrapText(order.notes, width)
            notesLines.forEach { sb.appendLine(it) }
        }

        // Device ID footer (last 6 characters)
        if (deviceId.isNotBlank()) {
            sb.appendLine()
            val shortId = if (deviceId.length > 6) deviceId.takeLast(6) else deviceId
            sb.appendLine(centerText(shortId, width))
        }

        return sb.toString()
    }

    /**
     * Center text within given width.
     */
    private fun centerText(text: String, width: Int): String {
        val truncated = if (text.length > width) text.take(width) else text
        val padding = (width - truncated.length) / 2
        return " ".repeat(padding) + truncated
    }

    /**
     * Format two columns: left-aligned text on left, right-aligned text on right.
     */
    private fun formatTwoColumns(left: String, right: String, width: Int): String {
        val maxLeftLength = width - right.length - 1
        val leftTruncated = if (left.length > maxLeftLength) left.take(maxLeftLength) else left
        val spaces = width - leftTruncated.length - right.length
        return leftTruncated + " ".repeat(maxOf(1, spaces)) + right
    }

    /**
     * Truncate text to max length, adding ellipsis if needed.
     */
    private fun truncateText(text: String, maxLength: Int): String {
        return if (text.length <= maxLength) {
            text
        } else {
            text.take(maxLength - 3) + "..."
        }
    }

    /**
     * Wrap long text into multiple lines.
     */
    private fun wrapText(text: String, width: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            if (currentLine.isEmpty()) {
                currentLine.append(word)
            } else if (currentLine.length + 1 + word.length <= width) {
                currentLine.append(" ").append(word)
            } else {
                lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            }
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }

        return lines
    }

    /**
     * Format quantity (remove trailing zeros for whole numbers).
     */
    private fun formatQuantity(quantity: Double): String {
        return if (quantity % 1 == 0.0) {
            String.format(Locale.getDefault(), "%.0f", quantity)
        } else {
            String.format(Locale.getDefault(), "%.3f", quantity)
        }
    }

    /**
     * Format price with 2 decimal places.
     */
    private fun formatPrice(price: Double): String {
        return String.format(Locale.getDefault(), "%.2f", price)
    }
}
