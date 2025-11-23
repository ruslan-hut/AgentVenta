package ua.com.programmer.agentventa.presentation.features.task

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ua.com.programmer.agentventa.data.local.entity.Task
import ua.com.programmer.agentventa.presentation.common.viewmodel.DocumentEvent
import ua.com.programmer.agentventa.domain.usecase.task.MarkTaskDoneUseCase
import ua.com.programmer.agentventa.domain.usecase.task.SaveTaskUseCase
import ua.com.programmer.agentventa.domain.usecase.task.ValidateTaskUseCase
import ua.com.programmer.agentventa.fake.FakeTaskRepository
import ua.com.programmer.agentventa.fake.FakeUserAccountRepository
import ua.com.programmer.agentventa.fixtures.TestFixtures
import ua.com.programmer.agentventa.infrastructure.logger.Logger
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
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
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
        taskRepository.clearAll() // Ensure clean state for each test
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
    fun `setCurrentDocument with invalid GUID loads empty task`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1()
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        // Act
        viewModel.setCurrentDocument("invalid-guid")
        advanceUntilIdle()

        // Assert - Repository returns empty task for invalid GUID
        viewModel.documentFlow.test {
            val currentTask = awaitItem()
            assertThat(currentTask.guid).isEqualTo("invalid-guid")
            assertThat(currentTask.time).isEqualTo(0L)
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
        viewModel.saveDocument()

        // Wait for save to complete
        viewModel.events.test {
            awaitItem() // SaveSuccess
            cancelAndIgnoreRemainingEvents()
        }

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

        // Act - editing to empty should fail validation on save
        viewModel.onEditDescription("")

        // This should emit SaveError because description is required
        viewModel.saveDocument()
        viewModel.events.test {
            val event = awaitItem()
            assertThat(event).isInstanceOf(DocumentEvent.SaveError::class.java)
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
        viewModel.saveDocument()

        // Wait for save
        viewModel.events.test {
            awaitItem() // SaveSuccess
            cancelAndIgnoreRemainingEvents()
        }

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
        viewModel.saveDocument()

        // Wait for save
        viewModel.events.test {
            awaitItem() // SaveSuccess
            cancelAndIgnoreRemainingEvents()
        }

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

        // Assert - Wait for documentFlow to update
        viewModel.documentFlow.test {
            val updatedTask = awaitItem()
            assertThat(updatedTask.isDone).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
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

        // Assert - Wait for documentFlow to update
        viewModel.documentFlow.test {
            val updatedTask = awaitItem()
            assertThat(updatedTask.isDone).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onEditDone toggles task status correctly`() = runTest {
        // Arrange
        val task = TestFixtures.createTask1().copy(isDone = 0)
        taskRepository.addTask(task)
        viewModel.setCurrentDocument(task.guid)
        advanceUntilIdle()

        // Act: Toggle to done
        viewModel.onEditDone(1)
        advanceUntilIdle()

        // Assert: Wait for repository to emit updated value
        val updated1 = taskRepository.getDocument(task.guid).first { it.isDone == 1 }
        assertThat(updated1.isDone).isEqualTo(1)

        // Assert: Check documentFlow reflects the change
        viewModel.documentFlow.test {
            val doc1 = awaitItem()
            assertThat(doc1.isDone).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }

        // Act: Toggle back to not done
        viewModel.onEditDone(0)
        advanceUntilIdle()

        // Assert: Wait for repository to emit updated value
        val updated2 = taskRepository.getDocument(task.guid).first { it.isDone == 0 }
        assertThat(updated2.isDone).isEqualTo(0)

        // Assert: Check documentFlow reflects the change
        viewModel.documentFlow.test {
            val doc2 = awaitItem()
            assertThat(doc2.isDone).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
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

        // Assert - Wait for documentFlow to update
        viewModel.documentFlow.test {
            val updatedTask = awaitItem()
            assertThat(updatedTask).isNotNull()
            assertThat(updatedTask.isDone).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
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

        // Act
        viewModel.saveDocument()

        // Wait for save event to ensure completion
        viewModel.events.test {
            val event = awaitItem()
            assertThat(event).isInstanceOf(DocumentEvent.SaveSuccess::class.java)
            cancelAndIgnoreRemainingEvents()
        }

        // Assert
        val savedTask = taskRepository.getDocument(task.guid).first()
        assertThat(savedTask.description).isEqualTo("Modified description")
        assertThat(savedTask.notes).isEqualTo("Modified notes")
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

        // Verify task exists before deletion
        val beforeDelete = taskRepository.getDocument(task.guid).first()
        assertThat(beforeDelete.time).isNotEqualTo(0L)

        // Act
        viewModel.deleteDocument()
        advanceUntilIdle()

        // Wait for delete event to confirm completion
        viewModel.events.test {
            val event = awaitItem()
            assertThat(event).isInstanceOf(DocumentEvent.DeleteSuccess::class.java)
            cancelAndIgnoreRemainingEvents()
        }

        // Assert - Deleted task returns empty task with time=0
        val deletedTask = taskRepository.getDocument(task.guid).first()
        assertThat(deletedTask.guid).isEqualTo(task.guid)
        assertThat(deletedTask.time).isEqualTo(0L)
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

    // ========================================
    // Edit Operations Tests
    // ========================================

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
        // Arrange - Create a new empty task
        viewModel.setCurrentDocument(null) // This creates a new document
        advanceUntilIdle()

        // Act
        viewModel.onEditDescription("New description")
        viewModel.onEditNotes("New notes")

        // Save to persist changes
        viewModel.saveDocument()
        viewModel.events.test {
            awaitItem() // SaveSuccess
            cancelAndIgnoreRemainingEvents()
        }

        // Assert - check that edits were saved
        val taskGuid = viewModel.getGuid()
        val savedTask = taskRepository.getDocument(taskGuid).first()
        assertThat(savedTask.description).isEqualTo("New description")
        assertThat(savedTask.notes).isEqualTo("New notes")
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

            val savedTask = taskRepository.getDocument(task.guid).first()
            assertThat(savedTask.description).isEqualTo(specialDescription)
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

        // Act
        viewModel.saveDocument()

        // Wait for save event to ensure completion
        viewModel.events.test {
            awaitItem() // SaveSuccess event
            cancelAndIgnoreRemainingEvents()
        }

        // Assert
        val savedTask = taskRepository.getDocument(task.guid).first()
        assertThat(savedTask.description).isEqualTo(unicodeDescription)
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

        // Save after all edits
        viewModel.saveDocument()
        viewModel.events.test {
            awaitItem() // SaveSuccess
            cancelAndIgnoreRemainingEvents()
        }

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

        // Act - Multiple toggles with flow updates between each
        viewModel.onEditDone(1)
        advanceUntilIdle()
        viewModel.documentFlow.test {
            assertThat(awaitItem().isDone).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.onEditDone(0)
        advanceUntilIdle()
        viewModel.documentFlow.test {
            assertThat(awaitItem().isDone).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.onEditDone(1)
        advanceUntilIdle()

        // Assert final state
        viewModel.documentFlow.test {
            val updatedTask = awaitItem()
            assertThat(updatedTask.isDone).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

}
