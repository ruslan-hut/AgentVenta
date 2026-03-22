package ua.com.programmer.agentventa.presentation.features.task

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.com.programmer.agentventa.data.local.entity.Task
import ua.com.programmer.agentventa.presentation.common.document.DocumentListViewModel
import ua.com.programmer.agentventa.domain.repository.TaskRepository
import ua.com.programmer.agentventa.domain.repository.UserAccountRepository
import javax.inject.Inject

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    userAccountRepository: UserAccountRepository
): DocumentListViewModel<Task>(taskRepository, userAccountRepository) {

    init {
        setTotalsVisible(true)
    }

    /**
     * Delete a task from the repository.
     * @param task The task to delete
     * @param onComplete Callback invoked after deletion completes
     */
    fun deleteTask(task: Task, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { taskRepository.deleteDocument(task) }
            onComplete()
        }
    }

    fun restoreTask(task: Task) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { taskRepository.insertOrUpdateDocument(task) }
        }
    }

    fun markTaskAsDone(task: Task, isDone: Boolean, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                taskRepository.updateDocument(task.copy(isDone = if (isDone) 1 else 0))
            }
            onComplete()
        }
    }

}