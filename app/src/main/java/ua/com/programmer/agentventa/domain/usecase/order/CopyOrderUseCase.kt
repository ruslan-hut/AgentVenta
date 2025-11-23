package ua.com.programmer.agentventa.domain.usecase.order

import ua.com.programmer.agentventa.data.local.entity.Order
import ua.com.programmer.agentventa.domain.result.DomainException
import ua.com.programmer.agentventa.domain.result.Result
import ua.com.programmer.agentventa.domain.usecase.SuspendUseCase
import ua.com.programmer.agentventa.domain.repository.OrderRepository
import javax.inject.Inject

/**
 * Use case for copying an existing order.
 * Creates a new order with the same client, company, store, and settings.
 */
class CopyOrderUseCase @Inject constructor(
    private val orderRepository: OrderRepository
) : SuspendUseCase<Order, Order>() {

    override suspend fun execute(params: Order): Result<Order> {
        // Create new order
        val newOrder = orderRepository.newDocument()
            ?: return Result.Error(DomainException.DatabaseError("Failed to create new order"))

        // Copy relevant fields from source order
        val copy = newOrder.copy(
            companyGuid = params.companyGuid,
            company = params.company,
            storeGuid = params.storeGuid,
            store = params.store,
            clientGuid = params.clientGuid,
            clientCode2 = params.clientCode2,
            clientDescription = params.clientDescription,
            priceType = params.priceType,
            paymentType = params.paymentType,
            isFiscal = params.isFiscal,
        )

        // Save the copy
        val saved = orderRepository.updateDocument(copy)
        return if (saved) {
            Result.Success(copy)
        } else {
            Result.Error(DomainException.DatabaseError("Failed to save copied order"))
        }
    }
}
