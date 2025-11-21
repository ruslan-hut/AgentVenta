package ua.com.programmer.agentventa.documents.common

import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.dao.entity.DocumentTotals
import ua.com.programmer.agentventa.dao.entity.UserAccount
import ua.com.programmer.agentventa.repository.DocumentRepository
import ua.com.programmer.agentventa.repository.UserAccountRepository
import java.util.Date

/**
 * UI state for document list screen.
 */
data class DocumentListUiState(
    val documentsCount: String = "-",
    val returnsCount: String = "-",
    val totalWeight: String = "0.0",
    val totalSum: String = "0.00",
    val noDataVisible: Boolean = true,
    val totalsVisible: Boolean = false,
    val searchVisible: Boolean = false,
    val searchText: String = "",
    val listDate: Date? = Date()
)

@OptIn(ExperimentalCoroutinesApi::class)
open class DocumentListViewModel<T>(
    private val repository: DocumentRepository<T>,
    private val userAccountRepository: UserAccountRepository,
    private val counterFormatter: CounterFormatter = DefaultCounterFormatter()
) : ViewModel() {

    // Current account state
    private val _currentAccount = MutableStateFlow<UserAccount?>(null)
    val currentAccount: StateFlow<UserAccount?> = _currentAccount.asStateFlow()

    // Search and filter state
    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private val _listDate = MutableStateFlow<Date?>(Date())
    val listDateFlow: StateFlow<Date?> = _listDate.asStateFlow()
    val listDate: androidx.lifecycle.LiveData<Date?> = _listDate.asLiveData()

    private val _searchVisible = MutableStateFlow(false)
    val searchVisible: StateFlow<Boolean> = _searchVisible.asStateFlow()

    // UI state combining all counters
    private val _uiState = MutableStateFlow(DocumentListUiState())
    val uiState: StateFlow<DocumentListUiState> = _uiState.asStateFlow()

    // Combined filter params for flatMapLatest
    private val filterParams = combine(
        _searchText,
        _listDate,
        _currentAccount
    ) { search, date, account ->
        ListParams(search, date, account)
    }

    // Documents list as StateFlow
    private val _documentsFlow: StateFlow<List<T>> = filterParams
        .flatMapLatest { params ->
            repository.getDocuments(params.filter, params.listDate)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )
    val documentsFlow: StateFlow<List<T>> get() = _documentsFlow
    val documents: androidx.lifecycle.LiveData<List<T>> = _documentsFlow.asLiveData()

    // Totals as StateFlow
    private val _totalsFlow: StateFlow<List<DocumentTotals>> = filterParams
        .flatMapLatest { params ->
            repository.getDocumentListTotals(params.filter, params.listDate)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )
    val totalsFlow: StateFlow<List<DocumentTotals>> get() = _totalsFlow
    val totals: androidx.lifecycle.LiveData<List<DocumentTotals>> = _totalsFlow.asLiveData()

    init {
        viewModelScope.launch {
            userAccountRepository.currentAccount.collect {
                _currentAccount.value = it
            }
        }

        // Auto-update UI state when totals change
        viewModelScope.launch {
            _totalsFlow.collect { totalsList ->
                updateCounters(totalsList)
            }
        }
    }

    fun toggleSearchVisibility() {
        val currentVisible = _searchVisible.value
        _searchVisible.value = !currentVisible
        if (currentVisible) {
            _searchText.value = ""
        }
        updateUiState { it.copy(searchVisible = !currentVisible) }
    }

    fun onTextChanged(text: CharSequence, start: Int, before: Int, count: Int) {
        _searchText.value = text.toString()
    }

    fun setSearchText(text: String) {
        _searchText.value = text
    }

    fun setDate(date: Date?) {
        _listDate.value = date
        updateUiState { it.copy(listDate = date) }
    }

    fun updateCounters(list: List<DocumentTotals>) {
        val documentTotals = if (list.isEmpty()) DocumentTotals() else list[0]
        val formatted = documentTotals.format(counterFormatter)

        updateUiState {
            it.copy(
                noDataVisible = !formatted.hasData,
                documentsCount = formatted.documentsCount,
                returnsCount = formatted.returnsCount,
                totalWeight = formatted.totalWeight,
                totalSum = formatted.totalSum
            )
        }
    }

    private fun updateUiState(transform: (DocumentListUiState) -> DocumentListUiState) {
        _uiState.value = transform(_uiState.value)
    }

    fun setTotalsVisible(visible: Boolean) {
        updateUiState { it.copy(totalsVisible = visible) }
    }

    // LiveData-compatible properties for XML data binding
    val searchVisibility: androidx.lifecycle.LiveData<Int> = _searchVisible
        .map { if (it) View.VISIBLE else View.GONE }
        .asLiveData()

    val noDataTextVisibility: androidx.lifecycle.LiveData<Int> = _uiState
        .map { if (it.noDataVisible) View.VISIBLE else View.GONE }
        .asLiveData()

    val totalsVisibility: androidx.lifecycle.LiveData<Int> = _uiState
        .map { if (it.totalsVisible) View.VISIBLE else View.GONE }
        .asLiveData()

    val documentsCount: androidx.lifecycle.LiveData<String> = _uiState
        .map { it.documentsCount }
        .asLiveData()

    val returnsCount: androidx.lifecycle.LiveData<String> = _uiState
        .map { it.returnsCount }
        .asLiveData()

    val totalWeight: androidx.lifecycle.LiveData<String> = _uiState
        .map { it.totalWeight }
        .asLiveData()

    val totalSum: androidx.lifecycle.LiveData<String> = _uiState
        .map { it.totalSum }
        .asLiveData()

}

data class ListParams(
    val filter: String = "",
    val listDate: Date? = null,
    val currentAccount: UserAccount? = null
)
