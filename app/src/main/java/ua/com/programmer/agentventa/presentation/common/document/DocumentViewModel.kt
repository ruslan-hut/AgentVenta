package ua.com.programmer.agentventa.presentation.common.document

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import ua.com.programmer.agentventa.domain.repository.DocumentRepository
import ua.com.programmer.agentventa.presentation.common.viewmodel.DocumentEvent
import ua.com.programmer.agentventa.presentation.common.viewmodel.EventChannel

/**
 * Base ViewModel for document management (Order, Cash, Task).
 * Uses StateFlow for reactive state and EventChannel for one-time events.
 *
 * @param T The document entity type
 * @param repository The document repository
 * @param logger Logger for error reporting
 * @param logTag Tag for logging
 * @param emptyDocument Factory for creating empty document instance
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class DocumentViewModel<T>(
    protected val repository: DocumentRepository<T>,
    protected val logger: Logger,
    protected val logTag: String,
    private val emptyDocument: () -> T
) : ViewModel() {

    // Document GUID state
    protected val _documentGuid = MutableStateFlow("")
    val documentGuid: StateFlow<String> = _documentGuid.asStateFlow()

    // Observable document from database using flatMapLatest
    private val _documentFlow: StateFlow<T> = _documentGuid
        .flatMapLatest { guid ->
            if (guid.isEmpty()) flowOf(emptyDocument())
            else repository.getDocument(guid)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyDocument()
        )
    val documentFlow: StateFlow<T> get() = _documentFlow
    val document: androidx.lifecycle.LiveData<T> = _documentFlow.asLiveData()

    // Current document value (non-null accessor)
    protected val currentDocument: T
        get() = _documentFlow.value

    // One-time events channel
    protected val _events = EventChannel<DocumentEvent>()
    val events = _events.flow

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Save result for backward compatibility with observe()
    private val _saveResult = MutableStateFlow<Boolean?>(null)
    val saveResult: androidx.lifecycle.LiveData<Boolean?> = _saveResult.asLiveData()

    /**
     * Set current document by GUID. Creates new document if ID is null/empty.
     */
    fun setCurrentDocument(id: String?) {
        // Reset save result to prevent immediate popBackStack on re-open
        _saveResult.value = null
        if (id.isNullOrEmpty()) {
            initNewDocument()
        } else {
            _documentGuid.value = id
        }
    }

    /**
     * Get current document GUID.
     */
    fun getGuid(): String = _documentGuid.value

    /**
     * Initialize a new document.
     */
    protected open fun initNewDocument() {
        viewModelScope.launch {
            _isLoading.value = true
            withContext(Dispatchers.IO) {
                val newDoc = repository.newDocument()
                if (newDoc != null) {
                    val guid = getDocumentGuid(newDoc)
                    _documentGuid.value = guid
                } else {
                    logger.e(logTag, "initNewDocument: failed to create new document")
                    _events.send(DocumentEvent.SaveError("Failed to create document"))
                }
            }
            _isLoading.value = false
        }
    }

    /**
     * Update document in database.
     */
    protected fun updateDocument(updated: T) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.updateDocument(updated)
            }
        }
    }

    /**
     * Update document and emit save result event.
     */
    protected fun updateDocumentWithResult(updated: T) {
        viewModelScope.launch {
            _isLoading.value = true
            withContext(Dispatchers.IO) {
                val saved = repository.updateDocument(updated)
                withContext(Dispatchers.Main) {
                    _saveResult.value = saved
                }
                if (saved) {
                    _events.send(DocumentEvent.SaveSuccess(getDocumentGuid(updated)))
                } else {
                    _events.send(DocumentEvent.SaveError("Failed to save document"))
                }
            }
            _isLoading.value = false
        }
    }

    /**
     * Delete current document.
     */
    open fun deleteDocument() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteDocument(currentDocument)
                _events.send(DocumentEvent.DeleteSuccess)
            }
        }
    }

    /**
     * Enable editing by resetting processed/sent flags.
     */
    abstract fun enableEdit()

    /**
     * Check if document is editable.
     */
    abstract fun isNotEditable(): Boolean

    /**
     * Edit notes field.
     */
    abstract fun onEditNotes(notes: String)

    /**
     * Clean up on destroy.
     */
    open fun onDestroy() {
        _documentGuid.value = ""
    }

    /**
     * Extract GUID from document entity.
     */
    protected abstract fun getDocumentGuid(document: T): String

    /**
     * Create updated document with processed flag set.
     */
    protected abstract fun markAsProcessed(document: T): T
}
