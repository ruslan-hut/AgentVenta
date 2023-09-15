package ua.com.programmer.agentventa.documents.order

import android.view.View
import dagger.hilt.android.lifecycle.HiltViewModel
import ua.com.programmer.agentventa.dao.entity.Order
import ua.com.programmer.agentventa.documents.common.DocumentListViewModel
import ua.com.programmer.agentventa.repository.OrderRepository
import ua.com.programmer.agentventa.repository.UserAccountRepository
import javax.inject.Inject

@HiltViewModel
class OrderListViewModel @Inject constructor(
    orderRepository: OrderRepository,
    userAccountRepository: UserAccountRepository
): DocumentListViewModel<Order>(orderRepository, userAccountRepository) {

    init {
        totalsVisibility.value = View.VISIBLE
    }

}