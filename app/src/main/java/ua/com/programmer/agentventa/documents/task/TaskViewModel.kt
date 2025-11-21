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

    private val task get() = currentDocument

    override fun getDocumentGuid(document: Task): String = document.guid

    override fun markAsProcessed(document: Task): Task = document

    override fun enableEdit() {
        // Tasks don't have processed/sent flags
        updateDocument(task)
    }

    override fun isNotEditable(): Boolean = false

    override fun onEditNotes(notes: String) {
        updateDocument(task.copy(notes = notes))
    }

    fun onEditDescription(description: String) {
        updateDocument(task.copy(description = description))
    }

    fun onEditDone(isDone: Int) {
        viewModelScope.launch {
            markTaskDoneUseCase(MarkTaskDoneUseCase.Params(task, isDone == 1))
        }
    }

    fun saveDocument() {
        updateDocumentWithResult(task)
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
