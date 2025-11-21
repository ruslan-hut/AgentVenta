package ua.com.programmer.agentventa.documents.order

import android.view.View
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.dao.entity.Order
import ua.com.programmer.agentventa.documents.common.DocumentListViewModel
import ua.com.programmer.agentventa.repository.OrderRepository
import ua.com.programmer.agentventa.repository.UserAccountRepository
import javax.inject.Inject

@HiltViewModel
class OrderListViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    userAccountRepository: UserAccountRepository
): DocumentListViewModel<Order>(orderRepository, userAccountRepository) {

    init {
        setTotalsVisible(true)
    }

    fun copyDocument(document: Order, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val newDocument = orderRepository.newDocument() ?: return@launch onResult("")
            val copy = newDocument.copy(
                companyGuid = document.companyGuid,
                company = document.company,
                storeGuid = document.storeGuid,
                store = document.store,
                clientGuid = document.clientGuid,
                clientCode2 = document.clientCode2,
                clientDescription = document.clientDescription,
                priceType = document.priceType,
                paymentType = document.paymentType,
                isFiscal = document.isFiscal,
            )
            if (orderRepository.updateDocument(copy)) {
                onResult(copy.guid)
            } else {
                onResult("")
            }
        }
    }
}