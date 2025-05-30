package ua.com.programmer.agentventa.documents.cash

import android.util.Log
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
import ua.com.programmer.agentventa.dao.entity.Client
import ua.com.programmer.agentventa.dao.entity.LClient
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

    // is calling on EditText action only
    fun onEditSum(enteredSum: String) {
        val sum = enteredSum.toDoubleOrNull() ?: 0.0
        updateDocument(currentDocument.copy(
            sum = sum,
            ))
    }

    fun onEditNotes(notes: String) {
        updateDocument(currentDocument.copy(
            notes = notes,
            isSent = 0,
            isProcessed = 0,
            ))
    }

    fun saveDocument(enteredSum: String) {
        val sum = enteredSum.toDoubleOrNull() ?: 0.0
        updateDocument(currentDocument.copy(
            isProcessed = 1,
            sum = sum,
        ))
    }

    fun onClientClick(client: LClient, popUp: () -> Unit) {
        val docGuid = currentDocument.guid
        Log.d("CashViewModel", "onClientClick: $client")
        viewModelScope.launch {
            val clientData = Client(
                guid = client.guid,
                description = client.description,
            )
            cashRepository.setClient(docGuid, clientData)
            withContext(Dispatchers.Main) {
                popUp()
            }
        }
    }

    fun deleteDocument() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                cashRepository.deleteDocument(currentDocument)
            }
        }
    }

    fun enableEdit() {
        updateDocument(currentDocument.copy(isProcessed = 0, isSent = 0))
    }

    fun onDestroy() {
        _documentGuid.value = ""
    }
}