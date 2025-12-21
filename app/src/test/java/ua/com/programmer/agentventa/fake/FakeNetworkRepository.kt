package ua.com.programmer.agentventa.fake

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ua.com.programmer.agentventa.data.local.entity.Cash
import ua.com.programmer.agentventa.data.local.entity.LOrderContent
import ua.com.programmer.agentventa.data.local.entity.LocationHistory
import ua.com.programmer.agentventa.data.local.entity.Order
import ua.com.programmer.agentventa.data.local.entity.ProductImage
import ua.com.programmer.agentventa.domain.repository.NetworkRepository
import ua.com.programmer.agentventa.data.remote.Result
import java.io.File

/**
 * Fake implementation of NetworkRepository for testing.
 * Simulates sync operations with configurable success/failure scenarios.
 */
class FakeNetworkRepository : NetworkRepository {

    private var shouldSucceed = true
    private var simulateDelay = false
    private var delayMillis = 100L
    private var progressSteps = emptyList<String>()
    private var errorMessage = "Network error occurred"

    override suspend fun updateAll(): Flow<Result> = flow {
        if (simulateDelay) delay(delayMillis)

        if (progressSteps.isNotEmpty()) {
            progressSteps.forEach { step ->
                emit(Result.Progress(step))
                if (simulateDelay) delay(delayMillis / progressSteps.size)
            }
        } else {
            emit(Result.Progress("Downloading clients..."))
            if (simulateDelay) delay(delayMillis / 4)

            emit(Result.Progress("Downloading products..."))
            if (simulateDelay) delay(delayMillis / 4)

            emit(Result.Progress("Downloading prices..."))
            if (simulateDelay) delay(delayMillis / 4)

            emit(Result.Progress("Downloading debts..."))
            if (simulateDelay) delay(delayMillis / 4)
        }

        if (shouldSucceed) {
            emit(Result.Success("Full sync completed successfully"))
        } else {
            emit(Result.Error(errorMessage))
        }
    }

    override suspend fun updateDifferential(): Flow<Result> = flow {
        if (simulateDelay) delay(delayMillis)

        if (progressSteps.isNotEmpty()) {
            progressSteps.forEach { step ->
                emit(Result.Progress(step))
                if (simulateDelay) delay(delayMillis / progressSteps.size)
            }
        } else {
            emit(Result.Progress("Uploading orders..."))
            if (simulateDelay) delay(delayMillis / 3)

            emit(Result.Progress("Uploading cash receipts..."))
            if (simulateDelay) delay(delayMillis / 3)

            emit(Result.Progress("Uploading images..."))
            if (simulateDelay) delay(delayMillis / 3)
        }

        if (shouldSucceed) {
            emit(Result.Success("Differential sync completed successfully"))
        } else {
            emit(Result.Error(errorMessage))
        }
    }

    override suspend fun getDebtContent(type: String, guid: String): Flow<Result> = flow {
        if (simulateDelay) delay(delayMillis)

        emit(Result.Progress("Loading debt details for $guid..."))

        if (shouldSucceed) {
            emit(Result.Success("Debt content loaded successfully"))
        } else {
            emit(Result.Error(errorMessage))
        }
    }

    override suspend fun getPrintData(guid: String, storage: File): Flow<Result> = flow {
        if (simulateDelay) delay(delayMillis)

        emit(Result.Progress("Generating PDF for $guid..."))

        if (shouldSucceed) {
            emit(Result.Success("PDF generated successfully"))
        } else {
            emit(Result.Error(errorMessage))
        }
    }

    // Test configuration methods

    /**
     * Configure whether sync operations should succeed or fail
     */
    fun setShouldSucceed(succeed: Boolean) {
        shouldSucceed = succeed
    }

    /**
     * Configure error message for failed operations
     */
    fun setErrorMessage(message: String) {
        errorMessage = message
    }

    /**
     * Enable/disable simulated network delay
     */
    fun setSimulateDelay(simulate: Boolean, millis: Long = 100L) {
        simulateDelay = simulate
        delayMillis = millis
    }

    /**
     * Set custom progress steps for sync operations
     */
    fun setProgressSteps(steps: List<String>) {
        progressSteps = steps
    }

    /**
     * Reset to default configuration
     */
    fun reset() {
        shouldSucceed = true
        simulateDelay = false
        delayMillis = 100L
        progressSteps = emptyList()
        errorMessage = "Network error occurred"
    }

    // WebSocket methods - stub implementations for testing

    override suspend fun uploadOrderViaWebSocket(order: Order, orderContent: List<LOrderContent>): Flow<Result> = flow {
        if (shouldSucceed) {
            emit(Result.Success("Order uploaded via WebSocket"))
        } else {
            emit(Result.Error(errorMessage))
        }
    }

    override suspend fun uploadCashViaWebSocket(cash: Cash): Flow<Result> = flow {
        if (shouldSucceed) {
            emit(Result.Success("Cash uploaded via WebSocket"))
        } else {
            emit(Result.Error(errorMessage))
        }
    }

    override suspend fun uploadImagesViaWebSocket(images: List<ProductImage>): Flow<Result> = flow {
        if (shouldSucceed) {
            emit(Result.Success("Images uploaded via WebSocket"))
        } else {
            emit(Result.Error(errorMessage))
        }
    }

    override suspend fun uploadLocationsViaWebSocket(locations: List<LocationHistory>): Flow<Result> = flow {
        if (shouldSucceed) {
            emit(Result.Success("Locations uploaded via WebSocket"))
        } else {
            emit(Result.Error(errorMessage))
        }
    }

    override suspend fun downloadCatalogsViaWebSocket(fullSync: Boolean): Flow<Result> = flow {
        if (shouldSucceed) {
            emit(Result.Success("Catalogs downloaded via WebSocket"))
        } else {
            emit(Result.Error(errorMessage))
        }
    }

    override suspend fun syncViaWebSocket(): Flow<Result> = flow {
        if (shouldSucceed) {
            emit(Result.Success("WebSocket sync completed"))
        } else {
            emit(Result.Error(errorMessage))
        }
    }
}
