package ua.com.programmer.agentventa.presentation.features.store

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.data.local.entity.Store
import ua.com.programmer.agentventa.domain.repository.OrderRepository
import javax.inject.Inject

@HiltViewModel
class ListViewModel @Inject constructor(
    private val orderRepository: OrderRepository
): ViewModel() {

    private val _listItems = MutableLiveData<List<Store>>()
    val listItems get() = _listItems
    private var orderGuid = ""

    init {
        viewModelScope.launch {
            _listItems.value = orderRepository.getStores()
        }
    }

    fun setOrderGuid(guid: String) {
        orderGuid = guid
    }

    fun setStore(store: Store, onResult: () -> Unit) {
        viewModelScope.launch {
            if (orderGuid.isNotBlank()) {
                orderRepository.setStore(orderGuid, store)
            }
            onResult()
        }
    }

}