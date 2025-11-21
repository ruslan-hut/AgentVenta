package ua.com.programmer.agentventa.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ua.com.programmer.agentventa.dao.entity.Task
import ua.com.programmer.agentventa.documents.DocumentTotals
import ua.com.programmer.agentventa.repository.TaskRepository
import java.util.*

/**
 * Fake implementation of TaskRepository for testing.
 * Provides in-memory storage for task documents.
 */
class FakeTaskRepository(
    private val currentAccountGuid: String = FakeUserAccountRepository.TEST_ACCOUNT_GUID
) : TaskRepository {

    private val tasks = MutableStateFlow<List<Task>>(emptyList())

    override fun getDocument(guid: String): Flow<Task> = tasks.map { list ->
        list.first { it.guid == guid }
    }

    override suspend fun newDocument(): Task {
        return Task(
            guid = UUID.randomUUID().toString(),
            db_guid = currentAccountGuid,
            date = Date(),
            time = Date(),
            isSent = 0,
            isProcessed = 0,
            isDone = 0
        )
    }

    override fun getDocuments(filter: String, listDate: Date?): Flow<List<Task>> = tasks.map { list ->
        list.filter { task ->
            val matchesFilter = filter.isEmpty() ||
                task.client?.contains(filter, ignoreCase = true) == true ||
                task.description?.contains(filter, ignoreCase = true) == true ||
                task.number?.contains(filter, ignoreCase = true) == true

            val matchesDate = listDate == null || isSameDay(task.date, listDate)

            matchesFilter && matchesDate
        }
    }

    override suspend fun updateDocument(document: Task): Boolean {
        val currentList = tasks.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.guid == document.guid }

        if (existingIndex >= 0) {
            currentList[existingIndex] = document
        } else {
            currentList.add(document)
        }

        tasks.value = currentList
        return true
    }

    override suspend fun deleteDocument(document: Task): Boolean {
        val currentList = tasks.value.toMutableList()
        val removed = currentList.removeIf { it.guid == document.guid }
        tasks.value = currentList
        return removed
    }

    override fun getDocumentListTotals(filter: String, listDate: Date?): Flow<List<DocumentTotals>> {
        return getDocuments(filter, listDate).map { taskList ->
            if (taskList.isEmpty()) {
                emptyList()
            } else {
                listOf(calculateTotals(taskList))
            }
        }
    }

    // Test helper methods

    fun addTask(task: Task) {
        tasks.value = tasks.value + task
    }

    fun clearAll() {
        tasks.value = emptyList()
    }

    private fun calculateTotals(taskList: List<Task>): DocumentTotals {
        return DocumentTotals(
            documents = taskList.count { it.isDone == 0 },
            returns = taskList.count { it.isDone == 1 }  // Using returns field for "done" count
        )
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}
