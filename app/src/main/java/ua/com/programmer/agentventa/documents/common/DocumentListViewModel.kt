package ua.com.programmer.agentventa.documents.common

import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    val listDate: StateFlow<Date?> = _listDate.asStateFlow()

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
    val documents: StateFlow<List<T>> = filterParams
        .flatMapLatest { params ->
            repository.getDocuments(params.filter, params.listDate)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Totals as StateFlow
    val totals: StateFlow<List<DocumentTotals>> = filterParams
        .flatMapLatest { params ->
            repository.getDocumentListTotals(params.filter, params.listDate)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            userAccountRepository.currentAccount.collect {
                _currentAccount.value = it
            }
        }

        // Auto-update UI state when totals change
        viewModelScope.launch {
            totals.collect { totalsList ->
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

    // Legacy LiveData-compatible properties for gradual migration
    @Deprecated("Use uiState.documentsCount instead")
    val documentsCountValue: String get() = _uiState.value.documentsCount

    @Deprecated("Use uiState.noDataVisible instead")
    val noDataTextVisibility: Int get() = if (_uiState.value.noDataVisible) View.VISIBLE else View.GONE
}

data class ListParams(
    val filter: String = "",
    val listDate: Date? = null,
    val currentAccount: UserAccount? = null
)
