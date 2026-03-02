package ua.com.programmer.agentventa.presentation.features.order

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.data.local.entity.Order
import ua.com.programmer.agentventa.presentation.common.document.DocumentListViewModel
import ua.com.programmer.agentventa.domain.result.Result
import ua.com.programmer.agentventa.domain.usecase.order.CopyOrderUseCase
import ua.com.programmer.agentventa.domain.repository.OrderRepository
import ua.com.programmer.agentventa.domain.repository.UserAccountRepository
import javax.inject.Inject

@HiltViewModel
class OrderListViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    userAccountRepository: UserAccountRepository,
    private val copyOrderUseCase: CopyOrderUseCase
): DocumentListViewModel<Order>(orderRepository, userAccountRepository) {

    init {
        setTotalsVisible(true)
    }

    fun copyDocument(document: Order, onResult: (String) -> Unit) {
        viewModelScope.launch {
            when (val result = copyOrderUseCase(document)) {
                is Result.Success -> onResult(result.data.guid)
                is Result.Error -> onResult("")
            }
        }
    }

    fun markReadyToSend(document: Order, onResult: () -> Unit) {
        viewModelScope.launch {
            val updated = document.copy(
                isSent = 0,
                isProcessed = 1,
                timeSaved = System.currentTimeMillis() / 1000
            )
            orderRepository.updateDocument(updated)
            onResult()
        }
    }
}