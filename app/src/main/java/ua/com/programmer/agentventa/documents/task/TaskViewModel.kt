package ua.com.programmer.agentventa.documents.task

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.com.programmer.agentventa.dao.entity.Task
import ua.com.programmer.agentventa.logger.Logger
import ua.com.programmer.agentventa.repository.TaskRepository
import javax.inject.Inject

@HiltViewModel
class TaskViewModel@Inject constructor(
    private val taskRepository: TaskRepository,
    private val logger: Logger
): ViewModel()   {
    private val _documentGuid = MutableLiveData("")
    private val currentDocument get() = document.value ?: Task(guid = "", time = 0L)

    val document = _documentGuid.switchMap {
        taskRepository.getDocument(it).asLiveData()
    }

    private fun updateDocument(updated: Task) {
        //updated.- = - //todo: replace '-' with actual value
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                taskRepository.updateDocument(updated)
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
                val document = taskRepository.newDocument()
                if (document != null) {
                    withContext(Dispatchers.Main) {
                        _documentGuid.value = document.guid
                    }
                } else {
                    logger.e("TaskViewModel", "initNewDocument: failed to create new document")
                }
            }
        }
    }

    fun onEditDescription(description: String) {
        updateDocument(currentDocument.copy(description = description))
    }

    fun onEditNotes(notes: String) {
        updateDocument(currentDocument.copy(notes = notes))
    }

    fun onEditDone(isDone: Int) {
        updateDocument(currentDocument.copy(isDone = isDone))
    }

    fun saveDocument() {
        updateDocument(currentDocument.copy(

        ))
    }

    fun deleteDocument() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                taskRepository.deleteDocument(currentDocument)
            }
        }
    }

    fun enableEdit() {
        updateDocument(currentDocument.copy()) //todo: is this function needed?
    }

    fun onDestroy() {
        _documentGuid.value = ""
    }
}