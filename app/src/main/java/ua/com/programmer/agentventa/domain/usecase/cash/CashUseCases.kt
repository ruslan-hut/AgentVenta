package ua.com.programmer.agentventa.domain.usecase.cash

import ua.com.programmer.agentventa.data.local.entity.Cash
import ua.com.programmer.agentventa.domain.result.DomainException
import ua.com.programmer.agentventa.domain.result.Result
import ua.com.programmer.agentventa.domain.usecase.SuspendUseCase
import ua.com.programmer.agentventa.domain.repository.CashRepository
import javax.inject.Inject

/**
 * Use case for validating a cash document before save.
 */
class ValidateCashUseCase @Inject constructor() : SuspendUseCase<Cash, Cash>() {

    override suspend fun execute(params: Cash): Result<Cash> {
        if (params.clientGuid.isEmpty()) {
            return Result.Error(
                DomainException.ValidationError("client", "Client is required")
            )
        }

        if (params.sum <= 0.0) {
            return Result.Error(
                DomainException.ValidationError("sum", "Sum must be greater than zero")
            )
        }

        return Result.Success(params)
    }
}

/**
 * Use case for saving/updating a cash document.
 */
class SaveCashUseCase @Inject constructor(
    private val cashRepository: CashRepository,
    private val validateCashUseCase: ValidateCashUseCase
) : SuspendUseCase<SaveCashUseCase.Params, Cash>() {

    data class Params(
        val cash: Cash,
        val markAsProcessed: Boolean = true
    )

    override suspend fun execute(params: Params): Result<Cash> {
        val validationResult = validateCashUseCase(params.cash)
        if (validationResult is Result.Error) {
            return validationResult
        }

        val cashToSave = if (params.markAsProcessed) {
            params.cash.copy(isProcessed = 1)
        } else {
            params.cash
        }

        val saved = cashRepository.updateDocument(cashToSave)
        return if (saved) {
            Result.Success(cashToSave)
        } else {
            Result.Error(DomainException.DatabaseError("Failed to save cash document"))
        }
    }
}

/**
 * Use case for creating a new cash document.
 */
class CreateCashUseCase @Inject constructor(
    private val cashRepository: CashRepository
) : SuspendUseCase<Unit, Cash>() {

    override suspend fun execute(params: Unit): Result<Cash> {
        val newCash = cashRepository.newDocument()
        return if (newCash != null) {
            Result.Success(newCash)
        } else {
            Result.Error(DomainException.DatabaseError("Failed to create new cash document"))
        }
    }
}

/**
 * Use case for deleting a cash document.
 */
class DeleteCashUseCase @Inject constructor(
    private val cashRepository: CashRepository
) : SuspendUseCase<Cash, Unit>() {

    override suspend fun execute(params: Cash): Result<Unit> {
        cashRepository.deleteDocument(params)
        return Result.Success(Unit)
    }
}

/**
 * Use case for enabling edit mode on a processed cash document.
 */
class EnableCashEditUseCase @Inject constructor(
    private val cashRepository: CashRepository
) : SuspendUseCase<Cash, Cash>() {

    override suspend fun execute(params: Cash): Result<Cash> {
        val editableCash = params.copy(isProcessed = 0, isSent = 0)
        val saved = cashRepository.updateDocument(editableCash)
        return if (saved) {
            Result.Success(editableCash)
        } else {
            Result.Error(DomainException.DatabaseError("Failed to enable edit mode"))
        }
    }
}
