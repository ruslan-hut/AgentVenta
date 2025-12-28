package ua.com.programmer.agentventa.domain.usecase.order

import android.content.SharedPreferences
import ua.com.programmer.agentventa.data.local.entity.Order
import ua.com.programmer.agentventa.domain.repository.OrderRepository
import ua.com.programmer.agentventa.domain.result.DomainException
import ua.com.programmer.agentventa.domain.result.Result
import ua.com.programmer.agentventa.domain.usecase.SuspendUseCase
import ua.com.programmer.agentventa.infrastructure.printer.OrderPrintFormatter
import java.io.File
import javax.inject.Inject

/**
 * Use case for generating printable text content for an order.
 *
 * Generates formatted text file suitable for thermal printer output.
 * Uses two-line product layout format.
 */
class GenerateOrderPrintUseCase @Inject constructor(
    private val orderRepository: OrderRepository,
    private val formatter: OrderPrintFormatter,
    private val preferences: SharedPreferences
) : SuspendUseCase<GenerateOrderPrintUseCase.Params, File>() {

    data class Params(
        val orderGuid: String,
        val outputDir: File,
        val companyName: String = "",
        val deviceId: String = ""
    )

    override suspend fun execute(params: Params): Result<File> {
        // Fetch order from database
        val order = orderRepository.getOrder(params.orderGuid)
            ?: return Result.Error(
                DomainException.NotFoundError("Order", params.orderGuid)
            )

        // Fetch order content
        val content = orderRepository.getContent(params.orderGuid)
        if (content.isEmpty()) {
            return Result.Error(
                DomainException.ValidationError("content", "Order has no content")
            )
        }

        // Get print width from preferences
        val printWidth = preferences.getInt("print_area_width", 32)

        // Use company from order if available, otherwise use provided
        val companyName = order.company.ifBlank { params.companyName }

        // Format order as text
        val text = formatter.format(
            order = order,
            content = content,
            width = printWidth,
            companyName = companyName,
            deviceId = params.deviceId
        )

        // Write to file
        val outputFile = File(params.outputDir, "${params.orderGuid}_order.txt")
        return try {
            outputFile.writeText(text)
            Result.Success(outputFile)
        } catch (e: Exception) {
            Result.Error(
                DomainException.DatabaseError("Failed to write print file: ${e.message}")
            )
        }
    }
}

/**
 * Data class representing order print data for webhook payload.
 */
data class OrderPrintData(
    val orderGuid: String,
    val orderNumber: Int,
    val date: String,
    val company: String,
    val store: String,
    val notes: String,
    val isReturn: Boolean,
    val total: Double,
    val discount: Double,
    val items: List<OrderPrintItem>
)

data class OrderPrintItem(
    val code: String,
    val description: String,
    val quantity: Double,
    val price: Double,
    val sum: Double
)

/**
 * Extension to convert Order and content to OrderPrintData.
 */
fun Order.toPrintData(content: List<ua.com.programmer.agentventa.data.local.entity.LOrderContent>): OrderPrintData {
    return OrderPrintData(
        orderGuid = guid,
        orderNumber = number,
        date = date,
        company = company,
        store = store,
        notes = notes,
        isReturn = isReturn == 1,
        total = price,
        discount = discountValue,
        items = content.map { item ->
            OrderPrintItem(
                code = item.code,
                description = item.description,
                quantity = item.quantity,
                price = item.price,
                sum = item.sum
            )
        }
    )
}
