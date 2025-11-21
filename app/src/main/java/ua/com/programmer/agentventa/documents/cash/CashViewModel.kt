package ua.com.programmer.agentventa.documents.cash

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.com.programmer.agentventa.dao.entity.Cash
import ua.com.programmer.agentventa.dao.entity.Client
import ua.com.programmer.agentventa.dao.entity.LClient
import ua.com.programmer.agentventa.documents.common.DocumentViewModel
import ua.com.programmer.agentventa.domain.result.DomainException
import ua.com.programmer.agentventa.domain.result.Result
import ua.com.programmer.agentventa.domain.usecase.cash.EnableCashEditUseCase
import ua.com.programmer.agentventa.domain.usecase.cash.SaveCashUseCase
import ua.com.programmer.agentventa.domain.usecase.cash.ValidateCashUseCase
import ua.com.programmer.agentventa.logger.Logger
import ua.com.programmer.agentventa.repository.CashRepository
import javax.inject.Inject

@HiltViewModel
class CashViewModel @Inject constructor(
    private val cashRepository: CashRepository,
    private val validateCashUseCase: ValidateCashUseCase,
    private val saveCashUseCase: SaveCashUseCase,
    private val enableCashEditUseCase: EnableCashEditUseCase,
    logger: Logger
) : DocumentViewModel<Cash>(
    repository = cashRepository,
    logger = logger,
    logTag = "CashVM",
    emptyDocument = { Cash(guid = "") }
) {

    private val cash get() = currentDocument

    override fun getDocumentGuid(document: Cash): String = document.guid

    override fun markAsProcessed(document: Cash): Cash =
        document.copy(isProcessed = 1)

    override fun enableEdit() {
        viewModelScope.launch {
            enableCashEditUseCase(cash)
        }
    }

    override fun isNotEditable(): Boolean = cash.isProcessed > 0

    override fun onEditNotes(notes: String) {
        updateDocument(cash.copy(notes = notes, isSent = 0, isProcessed = 0))
    }

    fun onEditFiscal(isFiscal: Int) {
        updateDocument(cash.copy(isFiscal = isFiscal))
    }

    fun onEditSum(enteredSum: String) {
        val sum = enteredSum.toDoubleOrNull() ?: 0.0
        updateDocument(cash.copy(sum = sum))
    }

    fun saveDocument(enteredSum: String) {
        val sum = enteredSum.toDoubleOrNull() ?: 0.0
        val updated = cash.copy(sum = sum)
        val processed = markAsProcessed(updated)
        updateDocumentWithResult(processed)
    }

    /**
     * Validate cash document using use case.
     * Returns validation error message or null if valid.
     */
    suspend fun validateCash(): String? {
        return when (val result = validateCashUseCase(cash)) {
            is Result.Success -> null
            is Result.Error -> {
                when (val ex = result.exception) {
                    is DomainException.ValidationError -> ex.message
                    else -> ex.message
                }
            }
        }
    }

    fun onClientClick(client: LClient, popUp: () -> Unit) {
        val docGuid = cash.guid
        viewModelScope.launch {
            val clientData = Client(
                guid = client.guid,
                description = client.description,
            )
            cashRepository.setClient(docGuid, clientData)
            withContext(Dispatchers.Main) {
                popUp()
            }
        }
    }
}
