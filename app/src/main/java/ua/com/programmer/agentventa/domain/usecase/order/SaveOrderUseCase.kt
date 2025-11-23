package ua.com.programmer.agentventa.domain.usecase.order

import ua.com.programmer.agentventa.data.local.entity.Order
import ua.com.programmer.agentventa.domain.result.DomainException
import ua.com.programmer.agentventa.domain.result.Result
import ua.com.programmer.agentventa.domain.usecase.SuspendUseCase
import ua.com.programmer.agentventa.domain.repository.OrderRepository
import javax.inject.Inject

/**
 * Use case for saving/updating an order.
 *
 * Handles:
 * - Validation before save
 * - Marking document as processed
 * - Updating timestamp
 */
class SaveOrderUseCase @Inject constructor(
    private val orderRepository: OrderRepository,
    private val validateOrderUseCase: ValidateOrderUseCase
) : SuspendUseCase<SaveOrderUseCase.Params, Order>() {

    data class Params(
        val order: Order,
        val markAsProcessed: Boolean = true
    )

    override suspend fun execute(params: Params): Result<Order> {
        // Validate order before saving
        val validationResult = validateOrderUseCase(params.order)
        if (validationResult is Result.Error) {
            return validationResult
        }

        // Prepare order for save
        val orderToSave = if (params.markAsProcessed) {
            params.order.copy(
                isProcessed = 1,
                timeSaved = System.currentTimeMillis() / 1000
            )
        } else {
            params.order
        }

        // Save to repository
        val saved = orderRepository.updateDocument(orderToSave)
        return if (saved) {
            Result.Success(orderToSave)
        } else {
            Result.Error(DomainException.DatabaseError("Failed to save order"))
        }
    }
}

/**
 * Use case for creating a new order.
 */
class CreateOrderUseCase @Inject constructor(
    private val orderRepository: OrderRepository
) : SuspendUseCase<Unit, Order>() {

    override suspend fun execute(params: Unit): Result<Order> {
        val newOrder = orderRepository.newDocument()
        return if (newOrder != null) {
            Result.Success(newOrder)
        } else {
            Result.Error(DomainException.DatabaseError("Failed to create new order"))
        }
    }
}

/**
 * Use case for deleting an order.
 */
class DeleteOrderUseCase @Inject constructor(
    private val orderRepository: OrderRepository
) : SuspendUseCase<Order, Unit>() {

    override suspend fun execute(params: Order): Result<Unit> {
        orderRepository.deleteDocument(params)
        return Result.Success(Unit)
    }
}

/**
 * Use case for validating an order before save.
 */
class ValidateOrderUseCase @Inject constructor() : SuspendUseCase<Order, Order>() {

    override suspend fun execute(params: Order): Result<Order> {
        // Client is required
        if (params.clientGuid.isNullOrEmpty()) {
            return Result.Error(
                DomainException.ValidationError("client", "Client is required")
            )
        }

        // For fiscal orders, additional validation
        if (params.isFiscal == 1) {
            if (params.price <= 0.0) {
                return Result.Error(
                    DomainException.ValidationError("price", "Price must be greater than zero for fiscal orders")
                )
            }
            if (params.quantity <= 0.0) {
                return Result.Error(
                    DomainException.ValidationError("quantity", "Quantity must be greater than zero for fiscal orders")
                )
            }
            if (params.paymentType.isEmpty()) {
                return Result.Error(
                    DomainException.ValidationError("paymentType", "Payment type is required for fiscal orders")
                )
            }
        }

        return Result.Success(params)
    }
}

/**
 * Use case for enabling edit mode on a processed order.
 */
class EnableOrderEditUseCase @Inject constructor(
    private val orderRepository: OrderRepository
) : SuspendUseCase<Order, Order>() {

    override suspend fun execute(params: Order): Result<Order> {
        val editableOrder = params.copy(isProcessed = 0, isSent = 0)
        val saved = orderRepository.updateDocument(editableOrder)
        return if (saved) {
            Result.Success(editableOrder)
        } else {
            Result.Error(DomainException.DatabaseError("Failed to enable edit mode"))
        }
    }
}
