package ua.com.programmer.agentventa.documents.task

import dagger.hilt.android.lifecycle.HiltViewModel
import ua.com.programmer.agentventa.dao.entity.Task
import ua.com.programmer.agentventa.documents.common.DocumentListViewModel
import ua.com.programmer.agentventa.repository.TaskRepository
import ua.com.programmer.agentventa.repository.UserAccountRepository
import javax.inject.Inject

@HiltViewModel
class TaskListViewModel @Inject constructor(
    taskRepository: TaskRepository,
    userAccountRepository: UserAccountRepository
): DocumentListViewModel<Task>(taskRepository, userAccountRepository) {

    init {
        setTotalsVisible(true)
    }

}