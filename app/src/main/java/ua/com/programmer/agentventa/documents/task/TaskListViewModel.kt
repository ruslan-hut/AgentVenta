package ua.com.programmer.agentventa.documents.task

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.com.programmer.agentventa.dao.entity.Task
import ua.com.programmer.agentventa.documents.common.DocumentListViewModel
import ua.com.programmer.agentventa.repository.TaskRepository
import ua.com.programmer.agentventa.repository.UserAccountRepository
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
            withContext(Dispatchers.IO) {
                taskRepository.deleteDocument(task)
            }
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    /**
     * Restore a deleted task (for undo functionality).
     * Uses insertOrUpdateDocument to re-insert the task into the database.
     * @param task The task to restore
     */
    fun restoreTask(task: Task) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                taskRepository.insertOrUpdateDocument(task)
            }
        }
    }

}