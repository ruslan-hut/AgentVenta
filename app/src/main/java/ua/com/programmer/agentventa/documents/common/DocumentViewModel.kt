package ua.com.programmer.agentventa.documents.common

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.com.programmer.agentventa.logger.Logger
import ua.com.programmer.agentventa.repository.DocumentRepository

/**
 * Base ViewModel for document management (Order, Cash, Task).
 * Provides common CRUD operations and state management.
 *
 * @param T The document entity type
 * @param repository The document repository
 * @param logger Logger for error reporting
 * @param logTag Tag for logging
 * @param emptyDocument Factory for creating empty document instance
 */
abstract class DocumentViewModel<T>(
    protected val repository: DocumentRepository<T>,
    protected val logger: Logger,
    protected val logTag: String,
    private val emptyDocument: () -> T
) : ViewModel() {

    // Document GUID state
    protected val _documentGuid = MutableLiveData("")

    // Observable document from database
    val document: LiveData<T> = _documentGuid.switchMap {
        repository.getDocument(it).asLiveData()
    }

    // Current document value (non-null accessor)
    protected val currentDocument: T
        get() = document.value ?: emptyDocument()

    // Save operation result
    val saveResult = MutableLiveData<Boolean?>()

    /**
     * Set current document by GUID. Creates new document if ID is null/empty.
     */
    fun setCurrentDocument(id: String?) {
        if (id.isNullOrEmpty()) {
            initNewDocument()
        } else {
            _documentGuid.value = id
        }
    }

    /**
     * Get current document GUID.
     */
    fun getGuid(): String = _documentGuid.value ?: ""

    /**
     * Initialize a new document.
     */
    protected open fun initNewDocument() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val newDoc = repository.newDocument()
                if (newDoc != null) {
                    val guid = getDocumentGuid(newDoc)
                    withContext(Dispatchers.Main) {
                        _documentGuid.value = guid
                    }
                } else {
                    logger.e(logTag, "initNewDocument: failed to create new document")
                }
            }
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
     * Update document and notify result.
     */
    protected fun updateDocumentWithResult(updated: T) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val saved = repository.updateDocument(updated)
                withContext(Dispatchers.Main) {
                    saveResult.value = saved
                }
            }
        }
    }

    /**
     * Delete current document.
     */
    open fun deleteDocument() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteDocument(currentDocument)
            }
        }
    }

    /**
     * Enable editing by resetting processed/sent flags.
     * Override to customize behavior.
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
        saveResult.value = null
    }

    /**
     * Extract GUID from document entity.
     * Must be implemented by subclasses since entities have different structures.
     */
    protected abstract fun getDocumentGuid(document: T): String

    /**
     * Create updated document with processed flag set.
     */
    protected abstract fun markAsProcessed(document: T): T
}
