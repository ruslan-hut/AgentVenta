package ua.com.programmer.agentventa.domain.usecase.task

import ua.com.programmer.agentventa.dao.entity.Task
import ua.com.programmer.agentventa.domain.result.DomainException
import ua.com.programmer.agentventa.domain.result.Result
import ua.com.programmer.agentventa.domain.usecase.SuspendUseCase
import ua.com.programmer.agentventa.repository.TaskRepository
import javax.inject.Inject

/**
 * Use case for validating a task before save.
 */
class ValidateTaskUseCase @Inject constructor() : SuspendUseCase<Task, Task>() {

    override suspend fun execute(params: Task): Result<Task> {
        if (params.description.isBlank()) {
            return Result.Error(
                DomainException.ValidationError("description", "Description is required")
            )
        }

        return Result.Success(params)
    }
}

/**
 * Use case for saving/updating a task.
 */
class SaveTaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val validateTaskUseCase: ValidateTaskUseCase
) : SuspendUseCase<Task, Task>() {

    override suspend fun execute(params: Task): Result<Task> {
        val validationResult = validateTaskUseCase(params)
        if (validationResult is Result.Error) {
            return validationResult
        }

        val saved = taskRepository.updateDocument(params)
        return if (saved) {
            Result.Success(params)
        } else {
            Result.Error(DomainException.DatabaseError("Failed to save task"))
        }
    }
}

/**
 * Use case for creating a new task.
 */
class CreateTaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) : SuspendUseCase<Unit, Task>() {

    override suspend fun execute(params: Unit): Result<Task> {
        val newTask = taskRepository.newDocument()
        return if (newTask != null) {
            Result.Success(newTask)
        } else {
            Result.Error(DomainException.DatabaseError("Failed to create new task"))
        }
    }
}

/**
 * Use case for deleting a task.
 */
class DeleteTaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) : SuspendUseCase<Task, Unit>() {

    override suspend fun execute(params: Task): Result<Unit> {
        taskRepository.deleteDocument(params)
        return Result.Success(Unit)
    }
}

/**
 * Use case for marking a task as done/undone.
 */
class MarkTaskDoneUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) : SuspendUseCase<MarkTaskDoneUseCase.Params, Task>() {

    data class Params(
        val task: Task,
        val isDone: Boolean
    )

    override suspend fun execute(params: Params): Result<Task> {
        val updatedTask = params.task.copy(isDone = if (params.isDone) 1 else 0)
        val saved = taskRepository.updateDocument(updatedTask)
        return if (saved) {
            Result.Success(updatedTask)
        } else {
            Result.Error(DomainException.DatabaseError("Failed to update task status"))
        }
    }
}
