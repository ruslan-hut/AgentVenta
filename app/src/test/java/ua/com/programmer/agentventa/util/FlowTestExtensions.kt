package ua.com.programmer.agentventa.util

import app.cash.turbine.Event
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.TestScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Test utilities for Flow testing with Turbine.
 * Provides helper functions for common Flow testing patterns.
 */

/**
 * Collects and returns all items emitted by the flow within the given timeout.
 * Useful for testing flows that emit a finite number of items.
 *
 * Example:
 * ```
 * val items = flow.collectItems(timeout = 1.seconds)
 * assertThat(items).containsExactly(item1, item2, item3)
 * ```
 */
suspend fun <T> Flow<T>.collectItems(
    timeout: Duration = 2.seconds
): List<T> {
    val items = mutableListOf<T>()
    test(timeout = timeout) {
        try {
            while (true) {
                items.add(awaitItem())
            }
        } catch (e: Exception) {
            // Flow completed or timed out
        }
    }
    return items
}

/**
 * Collects the first N items from the flow and returns them as a list.
 *
 * Example:
 * ```
 * val firstThree = flow.takeItems(3)
 * assertThat(firstThree).hasSize(3)
 * ```
 */
suspend fun <T> Flow<T>.takeItems(count: Int, timeout: Duration = 2.seconds): List<T> {
    val items = mutableListOf<T>()
    test(timeout = timeout) {
        repeat(count) {
            items.add(awaitItem())
        }
        cancelAndIgnoreRemainingEvents()
    }
    return items
}

/**
 * Asserts that the flow emits exactly the given items in order.
 *
 * Example:
 * ```
 * flow.assertEmits(item1, item2, item3)
 * ```
 */
suspend fun <T> Flow<T>.assertEmits(
    vararg expectedItems: T,
    timeout: Duration = 2.seconds
) {
    test(timeout = timeout) {
        expectedItems.forEach { expected ->
            val actual = awaitItem()
            if (actual != expected) {
                throw AssertionError("Expected $expected but got $actual")
            }
        }
        cancelAndIgnoreRemainingEvents()
    }
}

/**
 * Asserts that the flow emits the expected item and then completes.
 *
 * Example:
 * ```
 * flow.assertEmitsAndCompletes(expectedItem)
 * ```
 */
suspend fun <T> Flow<T>.assertEmitsAndCompletes(
    expectedItem: T,
    timeout: Duration = 2.seconds
) {
    test(timeout = timeout) {
        val actual = awaitItem()
        if (actual != expectedItem) {
            throw AssertionError("Expected $expectedItem but got $actual")
        }
        awaitComplete()
    }
}

/**
 * Asserts that the flow emits an error.
 *
 * Example:
 * ```
 * flow.assertEmitsError<IllegalStateException>()
 * ```
 */
suspend inline fun <reified E : Throwable> Flow<*>.assertEmitsError(
    timeout: Duration = 2.seconds
) {
    test(timeout = timeout) {
        val error = awaitError()
        if (error !is E) {
            throw AssertionError("Expected error of type ${E::class.simpleName} but got ${error::class.simpleName}")
        }
    }
}

/**
 * Asserts that the flow emits an error with a specific message.
 *
 * Example:
 * ```
 * flow.assertEmitsErrorWithMessage("Network error")
 * ```
 */
suspend fun Flow<*>.assertEmitsErrorWithMessage(
    expectedMessage: String,
    timeout: Duration = 2.seconds
) {
    test(timeout = timeout) {
        val error = awaitError()
        if (error.message != expectedMessage) {
            throw AssertionError("Expected error message '$expectedMessage' but got '${error.message}'")
        }
    }
}

/**
 * Asserts that the flow does not emit any items within the timeout period.
 *
 * Example:
 * ```
 * flow.assertNoEmission(timeout = 500.milliseconds)
 * ```
 */
suspend fun Flow<*>.assertNoEmission(timeout: Duration = 1.seconds) {
    test(timeout = timeout) {
        try {
            val item = awaitItem()
            throw AssertionError("Expected no emission but got $item")
        } catch (e: AssertionError) {
            // Expected - no emission occurred
            if (e.message?.contains("Expected no emission") == true) {
                throw e
            }
        }
        cancelAndIgnoreRemainingEvents()
    }
}

/**
 * Waits for the flow to emit an item that matches the predicate.
 * Returns the matching item.
 *
 * Example:
 * ```
 * val matchingItem = flow.awaitItemMatching { it.id == "test-id" }
 * ```
 */
suspend fun <T> Flow<T>.awaitItemMatching(
    timeout: Duration = 2.seconds,
    predicate: (T) -> Boolean
): T {
    var result: T? = null
    test(timeout = timeout) {
        while (result == null) {
            val item = awaitItem()
            if (predicate(item)) {
                result = item
                break
            }
        }
        cancelAndIgnoreRemainingEvents()
    }
    return result ?: throw AssertionError("No item matching predicate was emitted")
}

/**
 * Skips the first N items from the flow and returns the next item.
 *
 * Example:
 * ```
 * val thirdItem = flow.skipItemsAndTake(2)
 * ```
 */
suspend fun <T> Flow<T>.skipItemsAndTake(
    skipCount: Int,
    timeout: Duration = 2.seconds
): T {
    var result: T? = null
    test(timeout = timeout) {
        repeat(skipCount) {
            awaitItem()
        }
        result = awaitItem()
        cancelAndIgnoreRemainingEvents()
    }
    return result ?: throw AssertionError("Flow did not emit enough items")
}

/**
 * Collects items from the flow until the predicate returns true.
 * Returns all collected items including the one that satisfied the predicate.
 *
 * Example:
 * ```
 * val items = flow.collectUntil { it.status == "completed" }
 * ```
 */
suspend fun <T> Flow<T>.collectUntil(
    timeout: Duration = 2.seconds,
    predicate: (T) -> Boolean
): List<T> {
    val items = mutableListOf<T>()
    test(timeout = timeout) {
        while (true) {
            val item = awaitItem()
            items.add(item)
            if (predicate(item)) {
                break
            }
        }
        cancelAndIgnoreRemainingEvents()
    }
    return items
}

/**
 * Tests multiple flows concurrently using turbineScope.
 * Useful for testing flows that depend on each other.
 *
 * Example:
 * ```
 * testFlows {
 *     flow1.test {
 *         assertThat(awaitItem()).isEqualTo(expected1)
 *     }
 *     flow2.test {
 *         assertThat(awaitItem()).isEqualTo(expected2)
 *     }
 * }
 * ```
 */
suspend fun testFlows(block: suspend TestScope.() -> Unit) {
    turbineScope {
        block()
    }
}

/**
 * Asserts that a StateFlow has the expected value.
 *
 * Example:
 * ```
 * stateFlow.assertValue(expectedValue)
 * ```
 */
suspend fun <T> Flow<T>.assertValue(
    expectedValue: T,
    timeout: Duration = 2.seconds
) {
    test(timeout = timeout) {
        val actual = awaitItem()
        if (actual != expectedValue) {
            throw AssertionError("Expected $expectedValue but got $actual")
        }
        cancelAndIgnoreRemainingEvents()
    }
}

/**
 * Asserts that the flow emits values that satisfy all predicates in order.
 *
 * Example:
 * ```
 * flow.assertEmitsMatching(
 *     { it.status == "loading" },
 *     { it.status == "success" }
 * )
 * ```
 */
suspend fun <T> Flow<T>.assertEmitsMatching(
    vararg predicates: (T) -> Boolean,
    timeout: Duration = 2.seconds
) {
    test(timeout = timeout) {
        predicates.forEach { predicate ->
            val item = awaitItem()
            if (!predicate(item)) {
                throw AssertionError("Item $item did not match predicate")
            }
        }
        cancelAndIgnoreRemainingEvents()
    }
}

/**
 * Collects all events (items, completion, errors) from the flow.
 * Useful for comprehensive flow testing.
 *
 * Example:
 * ```
 * val events = flow.collectEvents()
 * assertThat(events).hasSize(3)
 * ```
 */
suspend fun <T> Flow<T>.collectEvents(timeout: Duration = 2.seconds): List<Event<T>> {
    val events = mutableListOf<Event<T>>()
    test(timeout = timeout) {
        try {
            while (true) {
                when (val event = awaitEvent()) {
                    is Event.Item -> events.add(event)
                    is Event.Complete -> {
                        events.add(event)
                        break
                    }
                    is Event.Error -> {
                        events.add(event)
                        break
                    }
                }
            }
        } catch (e: Exception) {
            // Timeout or other exception
        }
    }
    return events
}

/**
 * Asserts that the flow emits at least one item.
 *
 * Example:
 * ```
 * flow.assertHasEmission()
 * ```
 */
suspend fun <T> Flow<T>.assertHasEmission(timeout: Duration = 2.seconds): T {
    var result: T? = null
    test(timeout = timeout) {
        result = awaitItem()
        cancelAndIgnoreRemainingEvents()
    }
    return result ?: throw AssertionError("Flow did not emit any items")
}

/**
 * Extension function to get the latest value from a Flow (useful for StateFlow testing)
 *
 * Example:
 * ```
 * val currentValue = stateFlow.latestValue()
 * ```
 */
suspend fun <T> Flow<T>.latestValue(timeout: Duration = 1.seconds): T {
    return test(timeout = timeout) {
        awaitItem().also {
            cancelAndIgnoreRemainingEvents()
        }
    }
}
