package ua.com.programmer.agentventa.documents.cash

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.com.programmer.agentventa.dao.entity.Cash
import ua.com.programmer.agentventa.logger.Logger
import ua.com.programmer.agentventa.repository.CashRepository
import javax.inject.Inject

@HiltViewModel
class CashViewModel@Inject constructor(
    private val cashRepository: CashRepository,
    private val logger: Logger
): ViewModel()  {
    private val _documentGuid = MutableLiveData("")
    private val currentDocument get() = document.value ?: Cash(guid = "")

    val document = _documentGuid.switchMap {
        cashRepository.getDocument(it).asLiveData()
    }

    private fun updateDocument(updated: Cash) {
        //updated.- = - //todo: replace '-' with actual value
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                cashRepository.updateDocument(updated)
            }
        }
    }

    fun setCurrentDocument(id: String?) {
        if (id.isNullOrEmpty()) {
            initNewDocument()
        } else {
            _documentGuid.value = id
        }
    }

    private fun initNewDocument() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val document = cashRepository.newDocument()
                if (document != null) {
                    withContext(Dispatchers.Main) {
                        _documentGuid.value = document.guid
                    }
                } else {
                    logger.e("CashViewModel", "initNewDocument: failed to create new document")
                }
            }
        }
    }

    fun onEditFiscal(isFiscal: Int) {
        updateDocument(currentDocument.copy(isFiscal = isFiscal))
    }

    fun onEditNotes(notes: String) {
        updateDocument(currentDocument.copy(notes = notes))
    }

    fun saveDocument() {
        updateDocument(currentDocument.copy(
            isProcessed = 1,
            //todo: add other fields
        ))
    }

    fun deleteDocument() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                cashRepository.deleteDocument(currentDocument)
            }
        }
    }

    fun documentGuid() = _documentGuid.value ?: ""

    fun enableEdit() {
        updateDocument(currentDocument.copy(isProcessed = 0))
    }

    fun onDestroy() {
        _documentGuid.value = ""
    }
}