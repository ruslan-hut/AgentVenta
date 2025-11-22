package ua.com.programmer.agentventa.documents.task

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.dao.entity.Task
import ua.com.programmer.agentventa.documents.common.DocumentViewModel
import ua.com.programmer.agentventa.domain.result.DomainException
import ua.com.programmer.agentventa.domain.result.Result
import ua.com.programmer.agentventa.domain.usecase.task.MarkTaskDoneUseCase
import ua.com.programmer.agentventa.domain.usecase.task.SaveTaskUseCase
import ua.com.programmer.agentventa.domain.usecase.task.ValidateTaskUseCase
import ua.com.programmer.agentventa.logger.Logger
import ua.com.programmer.agentventa.repository.TaskRepository
import ua.com.programmer.agentventa.shared.DocumentEvent
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    taskRepository: TaskRepository,
    private val validateTaskUseCase: ValidateTaskUseCase,
    private val saveTaskUseCase: SaveTaskUseCase,
    private val markTaskDoneUseCase: MarkTaskDoneUseCase,
    logger: Logger
) : DocumentViewModel<Task>(
    repository = taskRepository,
    logger = logger,
    logTag = "TaskVM",
    emptyDocument = { Task(guid = "", time = 0L) }
) {

    // In-memory editable task to avoid race conditions with StateFlow updates
    private var _editableTask: Task? = null
    private var _currentGuid: String = ""

    private val task: Task
        get() {
            // If document GUID changed, reload from currentDocument
            if (_currentGuid != _documentGuid.value) {
                _currentGuid = _documentGuid.value
                _editableTask = null
            }
            // Initialize editable task on first access
            if (_editableTask == null) {
                _editableTask = currentDocument
            }
            return _editableTask!!
        }

    override fun getDocumentGuid(document: Task): String = document.guid

    override fun markAsProcessed(document: Task): Task = document

    override fun enableEdit() {
        // Tasks don't have processed/sent flags
        _editableTask = currentDocument
        updateDocument(task)
    }

    override fun isNotEditable(): Boolean = false

    override fun onEditNotes(notes: String) {
        _editableTask = task.copy(notes = notes)
        // Don't persist immediately - only update in-memory state
        // Actual save happens in saveDocument()
    }

    fun onEditDescription(description: String) {
        _editableTask = task.copy(description = description)
        // Don't persist immediately - only update in-memory state
        // Actual save happens in saveDocument()
    }

    fun onEditDone(isDone: Int) {
        // Capture task reference before launching coroutine
        val currentTask = task
        viewModelScope.launch {
            val result = markTaskDoneUseCase(MarkTaskDoneUseCase.Params(currentTask, isDone == 1))
            // Reset editable task so next access reloads from repository
            if (result is Result.Success) {
                _editableTask = null
            }
        }
    }

    fun saveDocument() {
        // Capture task reference BEFORE launching coroutine to avoid getter issues
        val taskToSave = task
        viewModelScope.launch {
            when (val result = saveTaskUseCase(taskToSave)) {
                is Result.Success -> {
                    _events.send(DocumentEvent.SaveSuccess(taskToSave.guid))
                }
                is Result.Error -> {
                    val message = when (val ex = result.exception) {
                        is DomainException.ValidationError -> ex.message
                        else -> ex.message
                    }
                    _events.send(DocumentEvent.SaveError(message))
                }
            }
        }
    }

    /**
     * Validate task using use case.
     * Returns validation error message or null if valid.
     */
    suspend fun validateTask(): String? {
        return when (val result = validateTaskUseCase(task)) {
            is Result.Success -> null
            is Result.Error -> {
                when (val ex = result.exception) {
                    is DomainException.ValidationError -> ex.message
                    else -> ex.message
                }
            }
        }
    }
}
