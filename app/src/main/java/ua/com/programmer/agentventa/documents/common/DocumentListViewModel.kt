package ua.com.programmer.agentventa.documents.common

import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.dao.entity.DocumentTotals
import ua.com.programmer.agentventa.dao.entity.UserAccount
import ua.com.programmer.agentventa.repository.DocumentRepository
import ua.com.programmer.agentventa.repository.UserAccountRepository
import java.util.Date

open class DocumentListViewModel<T> constructor(
    private val repository: DocumentRepository<T>,
    private val userAccountRepository: UserAccountRepository
): ViewModel() {

    val currentAccount = MutableLiveData<UserAccount>()
    val documentsCount = MutableLiveData("-")
    val returnsCount = MutableLiveData("-")
    val totalWeight = MutableLiveData("0.0")
    val totalSum = MutableLiveData("0.00")
    val listDate = MutableLiveData(Date())
    val noDataTextVisibility = MutableLiveData(View.VISIBLE)
    val totalsVisibility = MutableLiveData(View.GONE)
    val searchText = MutableLiveData("")
    val searchVisibility = MutableLiveData(View.GONE)

    private val mediator = MediatorLiveData<ListParams>().apply {
        addSource(searchText) { value = value?.copy(filter = it) ?: ListParams(filter = it) }
        addSource(listDate) { value = value?.copy(listDate = it) ?: ListParams(listDate = it) }
        addSource(currentAccount) { value = value?.copy(currentAccount = it) ?: ListParams(currentAccount = it) }
    }

    val documents : LiveData<List<T>> = mediator.switchMap { params ->
        repository.getDocuments(params.filter, params.listDate).asLiveData()
    }

    val totals : LiveData<List<DocumentTotals>> = mediator.switchMap { params ->
        repository.getDocumentListTotals(params.filter, params.listDate).asLiveData()
    }

    fun toggleSearchVisibility() {
        if (searchVisibility.value == View.VISIBLE) {
            searchVisibility.value = View.GONE
            searchText.value = ""
        } else {
            searchVisibility.value = View.VISIBLE
        }
    }

    /**
    Method is called in EditText, so it must have parameters like an EditText's method 'onTextChanged'
     */
    @Suppress("UNUSED_PARAMETER")
    fun onTextChanged(text: CharSequence, start: Int, before: Int, count: Int) {
        searchText.value = text.toString()
    }

    fun setDate(date: Date?) {
        listDate.value = date
    }

    fun updateCounters(list: List<DocumentTotals>) {
        val totals = if (list.isEmpty()) DocumentTotals() else list[0]
        if (totals.documents > 0 || totals.returns > 0) {
            noDataTextVisibility.value = View.GONE
        } else {
            noDataTextVisibility.value = View.VISIBLE
        }
        documentsCount.value = String.format("%d", totals.documents)
        returnsCount.value = String.format("%d", totals.returns)
        totalWeight.value = String.format("%.3f", totals.weight)
        totalSum.value = String.format("%.2f", totals.sum)
    }

    init {
        viewModelScope.launch {
            userAccountRepository.currentAccount.collect {
                currentAccount.value = it
            }
        }
    }

}

data class ListParams(
    val filter: String = "",
    val listDate: Date? = null,
    val currentAccount: UserAccount? = null
)