package ua.com.programmer.agentventa.documents.task

import dagger.hilt.android.lifecycle.HiltViewModel
import ua.com.programmer.agentventa.dao.entity.Task
import ua.com.programmer.agentventa.documents.common.DocumentViewModel
import ua.com.programmer.agentventa.logger.Logger
import ua.com.programmer.agentventa.repository.TaskRepository
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    taskRepository: TaskRepository,
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
        // Tasks don't have processed/sent flags in the same way
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
        updateDocument(task.copy(isDone = isDone))
    }

    fun saveDocument() {
        updateDocument(task)
    }
}
