package ua.com.programmer.agentventa.documents.order

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.dao.entity.Order
import ua.com.programmer.agentventa.documents.common.DocumentListViewModel
import ua.com.programmer.agentventa.domain.result.Result
import ua.com.programmer.agentventa.domain.usecase.order.CopyOrderUseCase
import ua.com.programmer.agentventa.repository.OrderRepository
import ua.com.programmer.agentventa.repository.UserAccountRepository
import javax.inject.Inject

@HiltViewModel
class OrderListViewModel @Inject constructor(
    orderRepository: OrderRepository,
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
}