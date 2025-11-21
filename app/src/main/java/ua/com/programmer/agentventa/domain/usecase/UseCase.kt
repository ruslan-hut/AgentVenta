package ua.com.programmer.agentventa.domain.usecase

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import ua.com.programmer.agentventa.domain.result.DomainException
import ua.com.programmer.agentventa.domain.result.Result

/**
 * Base interface for use cases that return a single result.
 *
 * @param P Parameters type
 * @param R Result type
 */
interface UseCase<in P, out R> {
    suspend operator fun invoke(params: P): Result<R>
}

/**
 * Base interface for use cases that return a Flow.
 *
 * @param P Parameters type
 * @param R Result type (emitted by Flow)
 */
interface FlowUseCase<in P, out R> {
    operator fun invoke(params: P): Flow<R>
}

/**
 * Base class for suspend use cases with error handling.
 */
abstract class SuspendUseCase<in P, out R>(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : UseCase<P, R> {

    override suspend operator fun invoke(params: P): Result<R> {
        return withContext(dispatcher) {
            try {
                execute(params)
            } catch (e: DomainException) {
                Result.Error(e)
            } catch (e: Exception) {
                Result.Error(DomainException.BusinessError(e.message ?: "Unknown error", e))
            }
        }
    }

    /**
     * Override this to implement the use case logic.
     */
    protected abstract suspend fun execute(params: P): Result<R>
}

/**
 * Base class for Flow use cases with error handling.
 */
abstract class FlowUseCaseBase<in P, out R>(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : FlowUseCase<P, R> {

    override operator fun invoke(params: P): Flow<R> {
        return execute(params)
            .catch { e ->
                // Log error but don't emit - let collector handle
                throw when (e) {
                    is DomainException -> e
                    else -> DomainException.BusinessError(e.message ?: "Unknown error", e)
                }
            }
            .flowOn(dispatcher)
    }

    /**
     * Override this to implement the use case logic.
     */
    protected abstract fun execute(params: P): Flow<R>
}

/**
 * Use case without parameters.
 */
abstract class NoParamUseCase<out R>(
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : SuspendUseCase<Unit, R>(dispatcher) {

    suspend operator fun invoke(): Result<R> = invoke(Unit)
}

/**
 * Flow use case without parameters.
 */
abstract class NoParamFlowUseCase<out R>(
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : FlowUseCaseBase<Unit, R>(dispatcher) {

    operator fun invoke(): Flow<R> = invoke(Unit)
}
