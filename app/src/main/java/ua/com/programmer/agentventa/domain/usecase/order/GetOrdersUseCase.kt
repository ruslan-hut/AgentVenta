package ua.com.programmer.agentventa.domain.usecase.order

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ua.com.programmer.agentventa.data.local.entity.Order
import ua.com.programmer.agentventa.domain.usecase.FlowUseCaseBase
import ua.com.programmer.agentventa.domain.repository.OrderRepository
import java.util.Date
import javax.inject.Inject

/**
 * Use case for fetching orders with filtering.
 *
 * Encapsulates the business logic for retrieving orders:
 * - Applies search filter
 * - Filters by date if specified
 * - Returns ordered list (newest first)
 */
class GetOrdersUseCase @Inject constructor(
    private val orderRepository: OrderRepository
) : FlowUseCaseBase<GetOrdersUseCase.Params, List<Order>>() {

    data class Params(
        val filter: String = "",
        val listDate: Date? = null
    )

    override fun execute(params: Params): Flow<List<Order>> {
        return orderRepository.getDocuments(params.filter, params.listDate)
    }
}

/**
 * Use case for fetching a single order by GUID.
 */
class GetOrderUseCase @Inject constructor(
    private val orderRepository: OrderRepository
) : FlowUseCaseBase<String, Order>() {

    override fun execute(params: String): Flow<Order> {
        return orderRepository.getDocument(params)
    }
}

/**
 * Use case for fetching order with content (header + lines).
 */
class GetOrderWithContentUseCase @Inject constructor(
    private val orderRepository: OrderRepository
) : FlowUseCaseBase<String, OrderWithContent>() {

    override fun execute(params: String): Flow<OrderWithContent> {
        return orderRepository.getDocument(params).map { order ->
            val content = orderRepository.getContent(order.guid)
            OrderWithContent(order, content ?: emptyList())
        }
    }
}

/**
 * Data class combining order header with content lines.
 */
data class OrderWithContent(
    val order: Order,
    val content: List<ua.com.programmer.agentventa.data.local.entity.LOrderContent>
)
