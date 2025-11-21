package ua.com.programmer.agentventa.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Test utilities for coroutine testing.
 * Provides helper functions for testing coroutines, delays, and async operations.
 */

/**
 * Creates a TestScope with a StandardTestDispatcher for testing.
 * StandardTestDispatcher requires manual advancement of time.
 *
 * Example:
 * ```
 * val scope = createTestScope()
 * scope.launch { /* coroutine code */ }
 * scope.advanceUntilIdle()
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun createTestScope(
    scheduler: TestCoroutineScheduler = TestCoroutineScheduler()
): TestScope {
    return TestScope(StandardTestDispatcher(scheduler))
}

/**
 * Creates a TestScope with UnconfinedTestDispatcher for testing.
 * UnconfinedTestDispatcher executes coroutines eagerly without delay.
 *
 * Example:
 * ```
 * val scope = createUnconfinedTestScope()
 * scope.launch { /* executes immediately */ }
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun createUnconfinedTestScope(): TestScope {
    return TestScope(UnconfinedTestDispatcher())
}

/**
 * Runs a test with automatic cleanup and exception handling.
 * Advances until idle automatically.
 *
 * Example:
 * ```
 * runTestWithCleanup {
 *     viewModel.loadData()
 *     advanceUntilIdle()
 *     // assertions
 * }
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun runTestWithCleanup(
    testBody: suspend TestScope.() -> Unit
) = runTest {
    try {
        testBody()
    } finally {
        // Cleanup is handled by runTest
    }
}

/**
 * Advances virtual time by the specified duration and runs pending coroutines.
 *
 * Example:
 * ```
 * testScope.advanceTimeBy(500.milliseconds)
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.advanceTimeBy(duration: Duration) {
    advanceTimeBy(duration.inWholeMilliseconds)
}

/**
 * Waits for all pending coroutines to complete.
 * Useful after launching coroutines to ensure they finish.
 *
 * Example:
 * ```
 * testScope.launch { delay(100) }
 * testScope.waitForPendingCoroutines()
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.waitForPendingCoroutines() {
    advanceUntilIdle()
}

/**
 * Runs the currently pending coroutines without advancing time.
 *
 * Example:
 * ```
 * testScope.launch { /* immediate work */ }
 * testScope.runCurrentCoroutines()
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.runCurrentCoroutines() {
    runCurrent()
}

/**
 * Asserts that a suspending block completes within the specified timeout.
 *
 * Example:
 * ```
 * assertCompletesWithin(1000.milliseconds) {
 *     repository.loadData()
 * }
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun TestScope.assertCompletesWithin(
    timeout: Duration,
    block: suspend () -> Unit
) {
    val startTime = currentTime
    block()
    val elapsed = currentTime - startTime
    if (elapsed > timeout.inWholeMilliseconds) {
        throw AssertionError("Operation took ${elapsed}ms but expected to complete within ${timeout.inWholeMilliseconds}ms")
    }
}

/**
 * Asserts that a suspending block throws an exception of the specified type.
 *
 * Example:
 * ```
 * assertThrows<IllegalStateException> {
 *     repository.invalidOperation()
 * }
 * ```
 */
suspend inline fun <reified T : Throwable> assertThrows(
    block: suspend () -> Unit
): T {
    try {
        block()
        throw AssertionError("Expected ${T::class.simpleName} to be thrown but no exception was thrown")
    } catch (e: Throwable) {
        if (e is T) {
            return e
        } else if (e is AssertionError) {
            throw e
        } else {
            throw AssertionError("Expected ${T::class.simpleName} but got ${e::class.simpleName}: ${e.message}")
        }
    }
}

/**
 * Asserts that a suspending block throws an exception with a specific message.
 *
 * Example:
 * ```
 * assertThrowsWithMessage("Invalid state") {
 *     repository.invalidOperation()
 * }
 * ```
 */
suspend fun assertThrowsWithMessage(
    expectedMessage: String,
    block: suspend () -> Unit
) {
    try {
        block()
        throw AssertionError("Expected exception with message '$expectedMessage' but no exception was thrown")
    } catch (e: Throwable) {
        if (e is AssertionError && e.message?.contains("Expected exception") == true) {
            throw e
        }
        if (e.message != expectedMessage) {
            throw AssertionError("Expected message '$expectedMessage' but got '${e.message}'")
        }
    }
}

/**
 * Asserts that a suspending block does not throw any exception.
 *
 * Example:
 * ```
 * assertDoesNotThrow {
 *     repository.safeOperation()
 * }
 * ```
 */
suspend fun assertDoesNotThrow(block: suspend () -> Unit) {
    try {
        block()
    } catch (e: Throwable) {
        throw AssertionError("Expected no exception but got ${e::class.simpleName}: ${e.message}")
    }
}

/**
 * Launches a coroutine and waits for it to complete.
 * Returns the job for further control.
 *
 * Example:
 * ```
 * val job = testScope.launchAndWait {
 *     repository.loadData()
 * }
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.launchAndWait(
    block: suspend CoroutineScope.() -> Unit
): Job {
    val job = launch(block = block)
    advanceUntilIdle()
    return job
}

/**
 * Creates a test dispatcher that can be controlled manually.
 * Useful for testing time-dependent code.
 *
 * Example:
 * ```
 * val dispatcher = createControlledTestDispatcher()
 * val scope = TestScope(dispatcher)
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun createControlledTestDispatcher(
    scheduler: TestCoroutineScheduler = TestCoroutineScheduler()
): TestDispatcher {
    return StandardTestDispatcher(scheduler)
}

/**
 * Simulates a delay in test without actually waiting.
 * Advances virtual time instead.
 *
 * Example:
 * ```
 * testScope.simulateDelay(500.milliseconds) {
 *     // Code that would normally wait 500ms executes immediately
 * }
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun TestScope.simulateDelay(duration: Duration, block: suspend () -> Unit) {
    block()
    advanceTimeBy(duration.inWholeMilliseconds)
}

/**
 * Asserts that coroutines are still running (haven't completed yet).
 *
 * Example:
 * ```
 * testScope.launch {
 *     delay(1000)
 *     completeWork()
 * }
 * testScope.assertHasPendingCoroutines()
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.assertHasPendingCoroutines() {
    val scheduler = testScheduler
    if (!scheduler.hasActiveDispatchers()) {
        throw AssertionError("Expected pending coroutines but none were found")
    }
}

/**
 * Asserts that no coroutines are pending (all completed).
 *
 * Example:
 * ```
 * testScope.advanceUntilIdle()
 * testScope.assertNoPendingCoroutines()
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.assertNoPendingCoroutines() {
    val scheduler = testScheduler
    if (scheduler.hasActiveDispatchers()) {
        throw AssertionError("Expected no pending coroutines but some were found")
    }
}

/**
 * Advances time until a specific condition is met.
 * Throws if condition is not met within maxTime.
 *
 * Example:
 * ```
 * testScope.advanceUntil(maxTime = 5000.milliseconds) {
 *     viewModel.isLoading.value == false
 * }
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.advanceUntil(
    maxTime: Duration = 10000.milliseconds,
    timeStep: Duration = 100.milliseconds,
    condition: () -> Boolean
) {
    val maxMillis = maxTime.inWholeMilliseconds
    val stepMillis = timeStep.inWholeMilliseconds
    var elapsed = 0L

    while (!condition() && elapsed < maxMillis) {
        advanceTimeBy(stepMillis)
        runCurrent()
        elapsed += stepMillis
    }

    if (!condition()) {
        throw AssertionError("Condition was not met within ${maxTime.inWholeMilliseconds}ms")
    }
}

/**
 * Collects values from a suspend function multiple times.
 * Useful for testing functions that return different values over time.
 *
 * Example:
 * ```
 * val results = collectResults(3) {
 *     repository.getCurrentState()
 * }
 * ```
 */
suspend fun <T> collectResults(
    count: Int,
    delayBetween: Duration = 100.milliseconds,
    block: suspend () -> T
): List<T> {
    val results = mutableListOf<T>()
    repeat(count) {
        results.add(block())
        delay(delayBetween.inWholeMilliseconds)
    }
    return results
}

/**
 * Ensures a test scope is properly cleaned up after test execution.
 *
 * Example:
 * ```
 * val scope = createTestScope()
 * scope.use {
 *     // Test code
 * }
 * // Scope is automatically cleaned up
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
inline fun <T> TestScope.use(block: TestScope.() -> T): T {
    try {
        return block()
    } finally {
        cancel()
    }
}

/**
 * Waits for a condition to become true with timeout.
 * Returns true if condition met, false if timeout.
 *
 * Example:
 * ```
 * val success = waitForCondition(timeout = 2000.milliseconds) {
 *     viewModel.data.value != null
 * }
 * ```
 */
suspend fun waitForCondition(
    timeout: Duration = 5000.milliseconds,
    checkInterval: Duration = 50.milliseconds,
    condition: () -> Boolean
): Boolean {
    val startTime = System.currentTimeMillis()
    val timeoutMillis = timeout.inWholeMilliseconds

    while (!condition()) {
        if (System.currentTimeMillis() - startTime > timeoutMillis) {
            return false
        }
        delay(checkInterval.inWholeMilliseconds)
    }
    return true
}

/**
 * Asserts that a condition becomes true within the specified timeout.
 *
 * Example:
 * ```
 * assertEventually(timeout = 1000.milliseconds) {
 *     viewModel.isReady.value == true
 * }
 * ```
 */
suspend fun assertEventually(
    timeout: Duration = 5000.milliseconds,
    checkInterval: Duration = 50.milliseconds,
    message: String = "Condition was not met within timeout",
    condition: () -> Boolean
) {
    val met = waitForCondition(timeout, checkInterval, condition)
    if (!met) {
        throw AssertionError("$message (timeout: ${timeout.inWholeMilliseconds}ms)")
    }
}

/**
 * Repeats an assertion multiple times with delay between attempts.
 * Useful for testing eventual consistency.
 *
 * Example:
 * ```
 * repeatAssertion(times = 3, delay = 100.milliseconds) {
 *     assertThat(viewModel.state.value).isEqualTo(expected)
 * }
 * ```
 */
suspend fun repeatAssertion(
    times: Int,
    delay: Duration = 100.milliseconds,
    assertion: suspend () -> Unit
) {
    repeat(times) {
        assertion()
        if (it < times - 1) {
            delay(delay.inWholeMilliseconds)
        }
    }
}
