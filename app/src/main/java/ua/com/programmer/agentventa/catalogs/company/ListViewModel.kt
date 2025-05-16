package ua.com.programmer.agentventa.catalogs.company

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.dao.entity.Company
import ua.com.programmer.agentventa.repository.OrderRepository
import javax.inject.Inject

@HiltViewModel
class ListViewModel @Inject constructor(
    private val orderRepository: OrderRepository
): ViewModel() {

    private val _listItems = MutableLiveData<List<Company>>()
    val listItems get() = _listItems
    private var orderGuid = ""

    init {
        viewModelScope.launch {
            _listItems.value = orderRepository.getCompanies()
        }
    }

    fun setOrderGuid(guid: String) {
        orderGuid = guid
    }

    fun setCompany(company: Company, onResult: () -> Unit) {
        viewModelScope.launch {
            if (orderGuid.isNotBlank()) {
                orderRepository.setCompany(orderGuid, company)
            }
            onResult()
        }
    }

}