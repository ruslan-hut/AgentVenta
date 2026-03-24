package ua.com.programmer.agentventa.extensions

/**
 * Centralized price and discount calculations for order lines.
 *
 * Discount convention:
 * - Negative percentage = discount (price reduction)
 * - Positive percentage = surcharge (price increase)
 * - Zero = no adjustment
 *
 * The monetary discount amount follows the same sign convention:
 * negative = reduces sum, positive = increases sum.
 */
object PriceCalculator {

    /**
     * Calculates the monetary discount adjustment for a line.
     *
     * @param lineSum the base line sum (price × quantity)
     * @param discountPercent the discount percentage (negative = discount, positive = surcharge)
     * @return monetary adjustment (negative = price reduced, positive = price increased)
     */
    fun calculateDiscountAmount(lineSum: Double, discountPercent: Double): Double {
        if (discountPercent == 0.0) return 0.0
        return lineSum * discountPercent / 100.0
    }

    /**
     * Calculates the final line sum after applying a discount.
     *
     * @param price unit price
     * @param quantity line quantity
     * @param discountPercent the discount percentage (negative = discount, positive = surcharge)
     * @return pair of (finalSum, discountAmount)
     */
    fun calculateLineWithDiscount(
        price: Double,
        quantity: Double,
        discountPercent: Double
    ): LineResult {
        val lineSum = calculateLineSum(price, quantity)
        val discountAmount = calculateDiscountAmount(lineSum, discountPercent)
        return LineResult(
            sum = lineSum + discountAmount,
            discount = discountAmount,
            lineSum = lineSum
        )
    }

    data class LineResult(
        val sum: Double,
        val discount: Double,
        val lineSum: Double,
    )
}
