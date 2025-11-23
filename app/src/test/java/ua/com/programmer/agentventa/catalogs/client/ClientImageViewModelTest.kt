package ua.com.programmer.agentventa.presentation.features.client

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import ua.com.programmer.agentventa.data.local.entity.ClientImage
import ua.com.programmer.agentventa.fixtures.TestFixtures
import ua.com.programmer.agentventa.domain.repository.FilesRepository
import ua.com.programmer.agentventa.util.MainDispatcherRule
import ua.com.programmer.agentventa.util.getOrAwaitValue

/**
 * Test suite for ClientImageViewModel
 *
 * Covers:
 * - Image loading by GUID
 * - Change description functionality
 * - Set as default functionality
 * - Delete image functionality
 * - Image state management
 * - Edge cases and error handling
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ClientImageViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var filesRepository: FilesRepository
    private lateinit var viewModel: ClientImageViewModel

    // Mock flow
    private lateinit var mockImageFlow: MutableStateFlow<ClientImage?>

    @Before
    fun setup() {
        // Initialize mock flow
        mockImageFlow = MutableStateFlow(null)

        // Mock repository
        filesRepository = mock {
            on { getClientImage(any()) } doReturn mockImageFlow
        }

        viewModel = ClientImageViewModel(filesRepository = filesRepository)
    }

    // ========================================
    // Helper Methods
    // ========================================

    private fun createTestImage(
        guid: String = "image-1",
        description: String = "Test Image",
        isDefault: Int = 0
    ) = ClientImage(
        guid = guid,
        clientGuid = "client-1",
        databaseId = TestFixtures.TEST_DB_GUID,
        url = "http://test.com/$guid.jpg",
        description = description,
        timestamp = System.currentTimeMillis(),
        isLocal = 0,
        isSent = 1,
        isDefault = isDefault
    )

    // ========================================
    // Initial State Tests
    // ========================================

    @Test
    fun `initial image is null`() = runTest {
        // Assert
        val image = viewModel.image.value
        assertThat(image).isNull()
    }

    // ========================================
    // Image Loading Tests
    // ========================================

    @Test
    fun `setImageParameters loads image from repository`() = runTest {
        // Arrange
        val testImage = createTestImage()
        mockImageFlow.value = testImage

        // Act
        viewModel.setImageParameters(testImage.guid)
        advanceUntilIdle()

        // Assert
        val loadedImage = viewModel.image.getOrAwaitValue()
        assertThat(loadedImage).isNotNull()
        assertThat(loadedImage.guid).isEqualTo(testImage.guid)
        assertThat(loadedImage.description).isEqualTo(testImage.description)

        verify(filesRepository).getClientImage(testImage.guid)
    }

    @Test
    fun `setImageParameters with null image creates empty image`() = runTest {
        // Arrange
        mockImageFlow.value = null

        // Act
        viewModel.setImageParameters("image-guid")
        advanceUntilIdle()

        // Assert
        val loadedImage = viewModel.image.getOrAwaitValue()
        assertThat(loadedImage).isNotNull()
        assertThat(loadedImage.guid).isEmpty()
    }

    @Test
    fun `image updates when repository emits new value`() = runTest {
        // Arrange
        val image1 = createTestImage("image-1", "First Image")
        viewModel.setImageParameters(image1.guid)
        mockImageFlow.value = image1
        advanceUntilIdle()

        // Act: Update image
        val image2 = createTestImage("image-1", "Updated Image")
        mockImageFlow.value = image2
        advanceUntilIdle()

        // Assert
        val loadedImage = viewModel.image.getOrAwaitValue()
        assertThat(loadedImage.description).isEqualTo("Updated Image")
    }

    @Test
    fun `loading different image updates state`() = runTest {
        // Arrange: First image
        val image1 = createTestImage("image-1")
        viewModel.setImageParameters(image1.guid)
        mockImageFlow.value = image1
        advanceUntilIdle()

        // Act: Load different image
        val image2 = createTestImage("image-2")
        viewModel.setImageParameters(image2.guid)
        mockImageFlow.value = image2
        advanceUntilIdle()

        // Assert
        val loadedImage = viewModel.image.getOrAwaitValue()
        assertThat(loadedImage.guid).isEqualTo("image-2")
    }

    // ========================================
    // Change Description Tests
    // ========================================

    @Test
    fun `changeDescription updates image description`() = runTest {
        // Arrange
        val image = createTestImage()
        mockImageFlow.value = image
        viewModel.setImageParameters(image.guid)
        advanceUntilIdle()

        // Act
        viewModel.changeDescription("New Description")
        advanceUntilIdle()

        // Assert
        verify(filesRepository).saveClientImage(
            image.copy(description = "New Description")
        )
    }

    @Test
    fun `changeDescription with empty string saves empty description`() = runTest {
        // Arrange
        val image = createTestImage()
        mockImageFlow.value = image
        viewModel.setImageParameters(image.guid)
        advanceUntilIdle()

        // Act
        viewModel.changeDescription("")
        advanceUntilIdle()

        // Assert
        verify(filesRepository).saveClientImage(
            image.copy(description = "")
        )
    }

    @Test
    fun `changeDescription with long text saves correctly`() = runTest {
        // Arrange
        val image = createTestImage()
        mockImageFlow.value = image
        viewModel.setImageParameters(image.guid)
        advanceUntilIdle()

        val longDescription = "A".repeat(1000)

        // Act
        viewModel.changeDescription(longDescription)
        advanceUntilIdle()

        // Assert
        verify(filesRepository).saveClientImage(
            image.copy(description = longDescription)
        )
    }

    @Test
    fun `changeDescription with special characters saves correctly`() = runTest {
        // Arrange
        val image = createTestImage()
        mockImageFlow.value = image
        viewModel.setImageParameters(image.guid)
        advanceUntilIdle()

        val specialDescription = "Image & Photo <Main> \"Test\""

        // Act
        viewModel.changeDescription(specialDescription)
        advanceUntilIdle()

        // Assert
        verify(filesRepository).saveClientImage(
            image.copy(description = specialDescription)
        )
    }

    @Test
    fun `changeDescription does nothing when image is null`() = runTest {
        // Act: Try to change description without loading image
        viewModel.changeDescription("New Description")
        advanceUntilIdle()

        // Assert: Repository should not be called
        verify(filesRepository, org.mockito.kotlin.never()).saveClientImage(any())
    }

    // ========================================
    // Set Default Tests
    // ========================================

    @Test
    fun `setDefault calls repository`() = runTest {
        // Arrange
        val image = createTestImage()
        mockImageFlow.value = image
        viewModel.setImageParameters(image.guid)
        advanceUntilIdle()

        // Act
        viewModel.setDefault()
        advanceUntilIdle()

        // Assert
        verify(filesRepository).setAsDefault(image)
    }

    @Test
    fun `setDefault works with already default image`() = runTest {
        // Arrange
        val image = createTestImage(isDefault = 1)
        mockImageFlow.value = image
        viewModel.setImageParameters(image.guid)
        advanceUntilIdle()

        // Act
        viewModel.setDefault()
        advanceUntilIdle()

        // Assert
        verify(filesRepository).setAsDefault(image)
    }

    @Test
    fun `setDefault does nothing when image is null`() = runTest {
        // Act: Try to set default without loading image
        viewModel.setDefault()
        advanceUntilIdle()

        // Assert: Repository should not be called
        verify(filesRepository, org.mockito.kotlin.never()).setAsDefault(any())
    }

    // ========================================
    // Delete Image Tests
    // ========================================

    @Test
    fun `deleteImage calls repository with image GUID`() = runTest {
        // Arrange
        val image = createTestImage()
        mockImageFlow.value = image
        viewModel.setImageParameters(image.guid)
        advanceUntilIdle()

        // Act
        viewModel.deleteImage()
        advanceUntilIdle()

        // Assert
        verify(filesRepository).deleteClientImage(image.guid)
    }

    @Test
    fun `deleteImage works with default image`() = runTest {
        // Arrange
        val image = createTestImage(isDefault = 1)
        mockImageFlow.value = image
        viewModel.setImageParameters(image.guid)
        advanceUntilIdle()

        // Act
        viewModel.deleteImage()
        advanceUntilIdle()

        // Assert
        verify(filesRepository).deleteClientImage(image.guid)
    }

    @Test
    fun `deleteImage does nothing when image is null`() = runTest {
        // Act: Try to delete without loading image
        viewModel.deleteImage()
        advanceUntilIdle()

        // Assert: Repository should not be called
        verify(filesRepository, org.mockito.kotlin.never()).deleteClientImage(any())
    }

    // ========================================
    // Multiple Operations Tests
    // ========================================

    @Test
    fun `multiple operations on same image work correctly`() = runTest {
        // Arrange
        val image = createTestImage()
        mockImageFlow.value = image
        viewModel.setImageParameters(image.guid)
        advanceUntilIdle()

        // Act: Multiple operations
        viewModel.changeDescription("Description 1")
        advanceUntilIdle()

        viewModel.changeDescription("Description 2")
        advanceUntilIdle()

        viewModel.setDefault()
        advanceUntilIdle()

        // Assert: All operations executed
        verify(filesRepository).saveClientImage(image.copy(description = "Description 1"))
        verify(filesRepository).saveClientImage(image.copy(description = "Description 2"))
        verify(filesRepository).setAsDefault(image)
    }

    @Test
    fun `changing description multiple times saves each time`() = runTest {
        // Arrange
        val image = createTestImage()
        mockImageFlow.value = image
        viewModel.setImageParameters(image.guid)
        advanceUntilIdle()

        // Act
        viewModel.changeDescription("Desc 1")
        viewModel.changeDescription("Desc 2")
        viewModel.changeDescription("Desc 3")
        advanceUntilIdle()

        // Assert
        verify(filesRepository).saveClientImage(image.copy(description = "Desc 1"))
        verify(filesRepository).saveClientImage(image.copy(description = "Desc 2"))
        verify(filesRepository).saveClientImage(image.copy(description = "Desc 3"))
    }

    // ========================================
    // Edge Cases and Error Handling Tests
    // ========================================

    @Test
    fun `loading image with all fields populated works correctly`() = runTest {
        // Arrange
        val fullImage = ClientImage(
            guid = "full-image",
            clientGuid = "client-123",
            databaseId = "db-456",
            url = "https://example.com/images/full-image.jpg",
            description = "Full image with all fields",
            timestamp = 1234567890L,
            isLocal = 1,
            isSent = 0,
            isDefault = 1
        )

        mockImageFlow.value = fullImage

        // Act
        viewModel.setImageParameters(fullImage.guid)
        advanceUntilIdle()

        // Assert
        val loadedImage = viewModel.image.getOrAwaitValue()
        assertThat(loadedImage.guid).isEqualTo("full-image")
        assertThat(loadedImage.clientGuid).isEqualTo("client-123")
        assertThat(loadedImage.databaseId).isEqualTo("db-456")
        assertThat(loadedImage.url).isEqualTo("https://example.com/images/full-image.jpg")
        assertThat(loadedImage.description).isEqualTo("Full image with all fields")
        assertThat(loadedImage.timestamp).isEqualTo(1234567890L)
        assertThat(loadedImage.isLocal).isEqualTo(1)
        assertThat(loadedImage.isSent).isEqualTo(0)
        assertThat(loadedImage.isDefault).isEqualTo(1)
    }

    @Test
    fun `local unsent image can be modified`() = runTest {
        // Arrange
        val localImage = createTestImage().copy(isLocal = 1, isSent = 0)
        mockImageFlow.value = localImage
        viewModel.setImageParameters(localImage.guid)
        advanceUntilIdle()

        // Act
        viewModel.changeDescription("Local image description")
        advanceUntilIdle()

        // Assert
        verify(filesRepository).saveClientImage(
            localImage.copy(description = "Local image description")
        )
    }

    @Test
    fun `image with empty URL loads correctly`() = runTest {
        // Arrange
        val imageNoUrl = createTestImage().copy(url = "")
        mockImageFlow.value = imageNoUrl

        // Act
        viewModel.setImageParameters(imageNoUrl.guid)
        advanceUntilIdle()

        // Assert
        val loadedImage = viewModel.image.getOrAwaitValue()
        assertThat(loadedImage.url).isEmpty()
    }

    @Test
    fun `image with very long URL loads correctly`() = runTest {
        // Arrange
        val longUrl = "https://example.com/" + "path/".repeat(100) + "image.jpg"
        val imageLongUrl = createTestImage().copy(url = longUrl)
        mockImageFlow.value = imageLongUrl

        // Act
        viewModel.setImageParameters(imageLongUrl.guid)
        advanceUntilIdle()

        // Assert
        val loadedImage = viewModel.image.getOrAwaitValue()
        assertThat(loadedImage.url).isEqualTo(longUrl)
    }

    @Test
    fun `unicode characters in description are preserved`() = runTest {
        // Arrange
        val image = createTestImage()
        mockImageFlow.value = image
        viewModel.setImageParameters(image.guid)
        advanceUntilIdle()

        val unicodeDescription = "Фото клієнта 你好 🎉"

        // Act
        viewModel.changeDescription(unicodeDescription)
        advanceUntilIdle()

        // Assert
        verify(filesRepository).saveClientImage(
            image.copy(description = unicodeDescription)
        )
    }

    @Test
    fun `setting same image parameters multiple times works correctly`() = runTest {
        // Arrange
        val image = createTestImage()
        mockImageFlow.value = image

        // Act: Set parameters multiple times
        viewModel.setImageParameters(image.guid)
        viewModel.setImageParameters(image.guid)
        viewModel.setImageParameters(image.guid)
        advanceUntilIdle()

        // Assert: Image loaded correctly
        val loadedImage = viewModel.image.getOrAwaitValue()
        assertThat(loadedImage.guid).isEqualTo(image.guid)
    }

    @Test
    fun `rapid operations execute in order`() = runTest {
        // Arrange
        val image = createTestImage()
        mockImageFlow.value = image
        viewModel.setImageParameters(image.guid)
        advanceUntilIdle()

        // Act: Rapid sequential operations
        viewModel.changeDescription("Desc 1")
        viewModel.setDefault()
        viewModel.changeDescription("Desc 2")
        viewModel.deleteImage()
        advanceUntilIdle()

        // Assert: All operations executed
        verify(filesRepository).saveClientImage(image.copy(description = "Desc 1"))
        verify(filesRepository).setAsDefault(image)
        verify(filesRepository).saveClientImage(image.copy(description = "Desc 2"))
        verify(filesRepository).deleteClientImage(image.guid)
    }

    @Test
    fun `image with timestamp zero loads correctly`() = runTest {
        // Arrange
        val imageNoTimestamp = createTestImage().copy(timestamp = 0L)
        mockImageFlow.value = imageNoTimestamp

        // Act
        viewModel.setImageParameters(imageNoTimestamp.guid)
        advanceUntilIdle()

        // Assert
        val loadedImage = viewModel.image.getOrAwaitValue()
        assertThat(loadedImage.timestamp).isEqualTo(0L)
    }

    @Test
    fun `changing description preserves other image fields`() = runTest {
        // Arrange
        val originalImage = ClientImage(
            guid = "preserve-test",
            clientGuid = "client-preserve",
            databaseId = "db-preserve",
            url = "http://preserve.com/image.jpg",
            description = "Original",
            timestamp = 999999L,
            isLocal = 1,
            isSent = 0,
            isDefault = 1
        )

        mockImageFlow.value = originalImage
        viewModel.setImageParameters(originalImage.guid)
        advanceUntilIdle()

        // Act
        viewModel.changeDescription("New Description")
        advanceUntilIdle()

        // Assert: Only description changed
        verify(filesRepository).saveClientImage(
            originalImage.copy(description = "New Description")
        )
    }
}
