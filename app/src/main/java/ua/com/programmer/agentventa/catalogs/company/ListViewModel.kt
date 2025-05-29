package ua.com.programmer.agentventa.catalogs.company

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.dao.entity.Company
import ua.com.programmer.agentventa.repository.CashRepository
import ua.com.programmer.agentventa.repository.OrderRepository
import ua.com.programmer.agentventa.shared.SharedParameters
import ua.com.programmer.agentventa.utility.Constants
import javax.inject.Inject

@HiltViewModel
class ListViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val cashRepository: CashRepository,
): ViewModel() {

    private val _listItems = MutableLiveData<List<Company>>()
    val listItems get() = _listItems
    private var docGuid = ""
    private var docType = ""

    init {
        viewModelScope.launch {
            _listItems.value = orderRepository.getCompanies()
        }
    }

    fun setListParameters(params: SharedParameters) {
        docType = params.docType
        docGuid = params.docGuid
    }

    fun setCompany(company: Company, onResult: () -> Unit) {
        viewModelScope.launch {
            if (docType.isBlank() || docGuid.isBlank()) {
                onResult
                return@launch
            }
            when (docType) {
                Constants.DOCUMENT_ORDER -> orderRepository.setCompany(docGuid, company)
                Constants.DOCUMENT_CASH -> cashRepository.setCompany(docGuid, company)
            }
            onResult()
        }
    }

}