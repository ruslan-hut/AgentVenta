package ua.com.programmer.agentventa.documents.task

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import ua.com.programmer.agentventa.dao.entity.Task
import ua.com.programmer.agentventa.documents.common.DocumentEvent
import ua.com.programmer.agentventa.domain.usecase.task.MarkTaskDoneUseCase
import ua.com.programmer.agentventa.domain.usecase.task.SaveTaskUseCase
import ua.com.programmer.agentventa.domain.usecase.task.ValidateTaskUseCase
import ua.com.programmer.agentventa.fake.FakeTaskRepository
import ua.com.programmer.agentventa.fake.FakeUserAccountRepository
import ua.com.programmer.agentventa.fixtures.TestFixtures
import ua.com.programmer.agentventa.logger.Logger
import ua.com.programmer.agentventa.util.MainDispatcherRule
import java.util.UUID

/**
 * Test suite for TaskViewModel
 *
 * Covers:
 * - Initial state and task creation
 * - Task loading and state management
 * - Description and notes editing
 * - Done status toggling
 * - Validation logic
 * - Save/delete operations
 * - Error handling and edge cases
 */
class TaskViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var taskRepository: FakeTaskRepository
    private lateinit var validateTaskUseCase: ValidateTaskUseCase
    private lateinit var saveTaskUseCase: SaveTaskUseCase
    private lateinit var markTaskDoneUseCase: MarkTaskDoneUseCase
    private lateinit var logger: Logger
    private lateinit var viewModel: TaskViewModel

    @Before
    fun setup() {
        taskRepository = FakeTaskRepository(FakeUserAccountRepository.TEST_ACCOUNT_GUID)
        logger = mock()

        // Use real use cases for accurate domain logic testing
        validateTaskUseCase = ValidateTaskUseCase()
        saveTaskUseCase = SaveTaskUseCase(taskRepository, validateTaskUseCase)
        markTaskDoneUseCase = MarkTaskDoneUseCase(taskRepository)

        viewModel = TaskViewModel(
            taskRepository = taskRepository,
            validateTaskUseCase = validateTaskUseCase,
            saveTaskUseCase = saveTaskUseCase,
            markTaskDoneUseCase = markTaskDoneUseCase,
            logger = logger
        )
    }

    // ========================================
    // Initial State Tests
    // ========================================

    @Test
    fun `initial state has empty task`() = runTest {
        viewModel.documentFlow.test {
            val task = awaitItem()
            assertThat(task.guid).isEmpty()
            assertThat(task.description).isEmpty()
            assertThat(task.notes).isEmpty()
            assertThat(task.isDone).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial loading state is false`() = runTest {
        viewModel.loadingFlow.test {
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `newDocument creates task with empty GUID`() = runTest {
        // Act
        viewModel.newDocument()
        advanceUntilIdle()

        // Assert
        viewModel.documentFlow.test {
            val task = awaitItem()
            assertThat(task.guid).isEmpty()
            assertThat(task.time).isGreaterThan(0L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // Task Loading Tests
    // ========================================

    @Test
    fun `setCurrentDocument loads task from repository`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1()
        taskRepository.addTask(task)

        // Act
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        // Assert
        viewModel.documentFlow.test {
            val loadedTask = awaitItem()
            assertThat(loadedTask.guid).isEqualTo(task.guid)
            assertThat(loadedTask.description).isEqualTo(task.description)
            assertThat(loadedTask.notes).isEqualTo(task.notes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setCurrentDocument with invalid GUID keeps previous task`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1()
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        // Act
        viewModel.setCurrentDocument("invalid-guid")
        advanceUntilIdle()

        // Assert
        viewModel.documentFlow.test {
            val currentTask = awaitItem()
            assertThat(currentTask.guid).isEqualTo(task.guid)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loading state is true during document load`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1()
        taskRepository.addTask(task)
        taskRepository.setLoadDelay(100L)

        // Act
        viewModel.setCurrentDocument(task.guid)

        // Assert
        viewModel.loadingFlow.test {
            assertThat(awaitItem()).isTrue()
            advanceUntilIdle()
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // Description and Notes Editing Tests
    // ========================================

    @Test
    fun `onEditDescription updates task description`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1()
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        // Act
        viewModel.onEditDescription("Updated description")
        advanceUntilIdle()

        // Assert
        viewModel.documentFlow.test {
            val updatedTask = awaitItem()
            assertThat(updatedTask.description).isEqualTo("Updated description")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onEditDescription with empty string clears description`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1()
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        // Act
        viewModel.onEditDescription("")
        advanceUntilIdle()

        // Assert
        viewModel.documentFlow.test {
            val updatedTask = awaitItem()
            assertThat(updatedTask.description).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onEditNotes updates task notes`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1()
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        // Act
        viewModel.onEditNotes("Updated notes for testing")
        advanceUntilIdle()

        // Assert
        viewModel.documentFlow.test {
            val updatedTask = awaitItem()
            assertThat(updatedTask.notes).isEqualTo("Updated notes for testing")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onEditNotes with multiline text preserves formatting`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1()
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        val multilineNotes = """
            Line 1
            Line 2
            Line 3
        """.trimIndent()

        // Act
        viewModel.onEditNotes(multilineNotes)
        advanceUntilIdle()

        // Assert
        viewModel.documentFlow.test {
            val updatedTask = awaitItem()
            assertThat(updatedTask.notes).isEqualTo(multilineNotes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // Done Status Toggle Tests
    // ========================================

    @Test
    fun `onEditDone sets task as done`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1().copy(isDone = 0)
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        // Act
        viewModel.onEditDone(1)
        advanceUntilIdle()

        // Assert
        val updatedTask = taskRepository.getDocument(task.guid).value
        assertThat(updatedTask?.isDone).isEqualTo(1)
    }

    @Test
    fun `onEditDone sets task as not done`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1().copy(isDone = 1)
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        // Act
        viewModel.onEditDone(0)
        advanceUntilIdle()

        // Assert
        val updatedTask = taskRepository.getDocument(task.guid).value
        assertThat(updatedTask?.isDone).isEqualTo(0)
    }

    @Test
    fun `onEditDone toggles task status correctly`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1().copy(isDone = 0)
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        // Act & Assert: Toggle to done
        viewModel.onEditDone(1)
        advanceUntilIdle()
        var updatedTask = taskRepository.getDocument(task.guid).value
        assertThat(updatedTask?.isDone).isEqualTo(1)

        // Act & Assert: Toggle back to not done
        viewModel.onEditDone(0)
        advanceUntilIdle()
        updatedTask = taskRepository.getDocument(task.guid).value
        assertThat(updatedTask?.isDone).isEqualTo(0)
    }

    @Test
    fun `onEditDone uses MarkTaskDoneUseCase`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1()
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        // Act
        viewModel.onEditDone(1)
        advanceUntilIdle()

        // Assert - Repository should be updated via use case
        val updatedTask = taskRepository.getDocument(task.guid).value
        assertThat(updatedTask).isNotNull()
        assertThat(updatedTask?.isDone).isEqualTo(1)
    }

    // ========================================
    // Validation Tests
    // ========================================

    @Test
    fun `validateTask returns null for valid task`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1()
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        // Act
        val error = viewModel.validateTask()

        // Assert
        assertThat(error).isNull()
    }

    @Test
    fun `validateTask returns error for blank description`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1().copy(description = "")
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        // Act
        val error = viewModel.validateTask()

        // Assert
        assertThat(error).isNotNull()
        assertThat(error).contains("Description is required")
    }

    @Test
    fun `validateTask returns error for whitespace-only description`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1().copy(description = "   ")
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        // Act
        val error = viewModel.validateTask()

        // Assert
        assertThat(error).isNotNull()
        assertThat(error).contains("Description is required")
    }

    @Test
    fun `validateTask allows empty notes`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1().copy(notes = "")
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        // Act
        val error = viewModel.validateTask()

        // Assert
        assertThat(error).isNull()
    }

    // ========================================
    // Save Operation Tests
    // ========================================

    @Test
    fun `saveDocument emits SaveSuccess event on valid task`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1()
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        // Act & Assert
        viewModel.events.test {
            viewModel.saveDocument()
            advanceUntilIdle()

            val event = awaitItem()
            assertThat(event).isInstanceOf(DocumentEvent.SaveSuccess::class.java)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveDocument emits SaveError event on invalid task`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1().copy(description = "")
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        // Act & Assert
        viewModel.events.test {
            viewModel.saveDocument()
            advanceUntilIdle()

            val event = awaitItem()
            assertThat(event).isInstanceOf(DocumentEvent.SaveError::class.java)
            val errorEvent = event as DocumentEvent.SaveError
            assertThat(errorEvent.message).contains("Description is required")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveDocument persists changes to repository`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1()
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        viewModel.onEditDescription("Modified description")
        viewModel.onEditNotes("Modified notes")
        advanceUntilIdle()

        // Act
        viewModel.saveDocument()
        advanceUntilIdle()

        // Assert
        val savedTask = taskRepository.getDocument(task.guid).value
        assertThat(savedTask?.description).isEqualTo("Modified description")
        assertThat(savedTask?.notes).isEqualTo("Modified notes")
    }

    @Test
    fun `saveDocument sets loading state during save`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1()
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()
        taskRepository.setSaveDelay(100L)

        // Act
        viewModel.saveDocument()

        // Assert
        viewModel.loadingFlow.test {
            assertThat(awaitItem()).isTrue()
            advanceUntilIdle()
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // Delete Operation Tests
    // ========================================

    @Test
    fun `deleteDocument removes task from repository`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1()
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        // Act
        viewModel.deleteDocument()
        advanceUntilIdle()

        // Assert
        val deletedTask = taskRepository.getDocument(task.guid).value
        assertThat(deletedTask).isNull()
    }

    @Test
    fun `deleteDocument emits DeleteSuccess event`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1()
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        // Act & Assert
        viewModel.events.test {
            viewModel.deleteDocument()
            advanceUntilIdle()

            val event = awaitItem()
            assertThat(event).isInstanceOf(DocumentEvent.DeleteSuccess::class.java)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteDocument sets loading state during deletion`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1()
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()
        taskRepository.setDeleteDelay(100L)

        // Act
        viewModel.deleteDocument()

        // Assert
        viewModel.loadingFlow.test {
            assertThat(awaitItem()).isTrue()
            advanceUntilIdle()
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // Abstract Method Implementation Tests
    // ========================================

    @Test
    fun `getDocumentGuid returns task guid`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1()

        // Act
        val guid = viewModel.getDocumentGuid(task)

        // Assert
        assertThat(guid).isEqualTo(task.guid)
    }

    @Test
    fun `markAsProcessed returns same task unchanged`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1()

        // Act
        val result = viewModel.markAsProcessed(task)

        // Assert
        assertThat(result).isEqualTo(task)
    }

    @Test
    fun `isNotEditable always returns false`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1()
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        // Act
        val notEditable = viewModel.isNotEditable()

        // Assert
        assertThat(notEditable).isFalse()
    }

    @Test
    fun `enableEdit updates current task`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1()
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        // Act
        viewModel.enableEdit()
        advanceUntilIdle()

        // Assert - Task should remain unchanged since tasks don't have processed/sent flags
        viewModel.documentFlow.test {
            val currentTask = awaitItem()
            assertThat(currentTask.guid).isEqualTo(task.guid)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // Edge Cases and Error Handling Tests
    // ========================================

    @Test
    fun `editing empty task does not crash`() = runTest {
        // Act & Assert
        viewModel.onEditDescription("New description")
        viewModel.onEditNotes("New notes")
        advanceUntilIdle()

        viewModel.documentFlow.test {
            val task = awaitItem()
            assertThat(task.description).isEqualTo("New description")
            assertThat(task.notes).isEqualTo("New notes")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saving task with very long description succeeds`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1()
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        val longDescription = "A".repeat(5000)
        viewModel.onEditDescription(longDescription)
        advanceUntilIdle()

        // Act & Assert
        viewModel.events.test {
            viewModel.saveDocument()
            advanceUntilIdle()

            val event = awaitItem()
            assertThat(event).isInstanceOf(DocumentEvent.SaveSuccess::class.java)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saving task with special characters in description succeeds`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1()
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        val specialDescription = "Task <>&\"'@#$%^&*()[]{}|\\:;,.<>?/~`"
        viewModel.onEditDescription(specialDescription)
        advanceUntilIdle()

        // Act & Assert
        viewModel.events.test {
            viewModel.saveDocument()
            advanceUntilIdle()

            val event = awaitItem()
            assertThat(event).isInstanceOf(DocumentEvent.SaveSuccess::class.java)

            val savedTask = taskRepository.getDocument(task.guid).value
            assertThat(savedTask?.description).isEqualTo(specialDescription)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `task with unicode characters in description saves correctly`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1()
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        val unicodeDescription = "Завдання з українськими символами 你好 🎉"
        viewModel.onEditDescription(unicodeDescription)
        advanceUntilIdle()

        // Act
        viewModel.saveDocument()
        advanceUntilIdle()

        // Assert
        val savedTask = taskRepository.getDocument(task.guid).value
        assertThat(savedTask?.description).isEqualTo(unicodeDescription)
    }

    @Test
    fun `multiple rapid edits only persist final state`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1()
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        // Act - Rapid edits
        viewModel.onEditDescription("First")
        viewModel.onEditDescription("Second")
        viewModel.onEditDescription("Third")
        viewModel.onEditDescription("Final description")
        advanceUntilIdle()

        // Assert
        viewModel.documentFlow.test {
            val currentTask = awaitItem()
            assertThat(currentTask.description).isEqualTo("Final description")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggling done status multiple times works correctly`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1().copy(isDone = 0)
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        // Act - Multiple toggles
        viewModel.onEditDone(1)
        advanceUntilIdle()
        viewModel.onEditDone(0)
        advanceUntilIdle()
        viewModel.onEditDone(1)
        advanceUntilIdle()

        // Assert
        val updatedTask = taskRepository.getDocument(task.guid).value
        assertThat(updatedTask?.isDone).isEqualTo(1)
    }

    @Test
    fun `repository error during save emits SaveError event`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1()
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        taskRepository.setShouldFailSave(true)

        // Act & Assert
        viewModel.events.test {
            viewModel.saveDocument()
            advanceUntilIdle()

            val event = awaitItem()
            assertThat(event).isInstanceOf(DocumentEvent.SaveError::class.java)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `new task has correct initial timestamp`() = runTest {
        // Arrange
        val timestampBefore = System.currentTimeMillis()

        // Act
        viewModel.newDocument()
        advanceUntilIdle()

        // Assert
        viewModel.documentFlow.test {
            val task = awaitItem()
            assertThat(task.time).isAtLeast(timestampBefore)
            assertThat(task.time).isAtMost(System.currentTimeMillis())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `task creation preserves all default values`() = runTest {
        // Act
        viewModel.newDocument()
        advanceUntilIdle()

        // Assert
        viewModel.documentFlow.test {
            val task = awaitItem()
            assertThat(task.guid).isEmpty()
            assertThat(task.description).isEmpty()
            assertThat(task.notes).isEmpty()
            assertThat(task.isDone).isEqualTo(0)
            assertThat(task.time).isGreaterThan(0L)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
