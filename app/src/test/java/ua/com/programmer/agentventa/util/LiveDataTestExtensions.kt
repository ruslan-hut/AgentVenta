package ua.com.programmer.agentventa.util

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Test utilities for LiveData observation and testing.
 * Provides helper functions for observing and asserting LiveData values in tests.
 */

/**
 * Observer that collects all values emitted by LiveData.
 * Use this to capture multiple emissions for assertion.
 */
class TestObserver<T> : Observer<T> {
    private val values = mutableListOf<T>()
    private val errors = mutableListOf<Throwable>()

    override fun onChanged(value: T) {
        if (value is Throwable) {
            errors.add(value)
        } else {
            values.add(value)
        }
    }

    fun getValues(): List<T> = values.toList()

    fun getErrors(): List<Throwable> = errors.toList()

    fun assertNoValues() {
        if (values.isNotEmpty()) {
            throw AssertionError("Expected no values but got ${values.size}: $values")
        }
    }

    fun assertValueCount(count: Int) {
        if (values.size != count) {
            throw AssertionError("Expected $count values but got ${values.size}: $values")
        }
    }

    fun assertValue(expected: T) {
        if (values.isEmpty()) {
            throw AssertionError("Expected value $expected but LiveData emitted no values")
        }
        val actual = values.last()
        if (actual != expected) {
            throw AssertionError("Expected $expected but got $actual")
        }
    }

    fun assertValues(vararg expected: T) {
        if (values.size != expected.size) {
            throw AssertionError("Expected ${expected.size} values but got ${values.size}")
        }
        expected.forEachIndexed { index, expectedValue ->
            if (values[index] != expectedValue) {
                throw AssertionError("At index $index: expected $expectedValue but got ${values[index]}")
            }
        }
    }

    fun assertValueMatches(predicate: (T) -> Boolean) {
        if (values.isEmpty()) {
            throw AssertionError("Expected matching value but LiveData emitted no values")
        }
        val actual = values.last()
        if (!predicate(actual)) {
            throw AssertionError("Value $actual does not match predicate")
        }
    }

    fun assertHasValue(): T {
        if (values.isEmpty()) {
            throw AssertionError("Expected LiveData to have a value but it was empty")
        }
        return values.last()
    }

    fun assertError() {
        if (errors.isEmpty()) {
            throw AssertionError("Expected an error but none occurred")
        }
    }

    fun assertNoErrors() {
        if (errors.isNotEmpty()) {
            throw AssertionError("Expected no errors but got ${errors.size}: $errors")
        }
    }
}

/**
 * Gets the current value from LiveData synchronously.
 * Blocks until a value is emitted or timeout occurs.
 *
 * Example:
 * ```
 * val value = liveData.getOrAwaitValue()
 * assertThat(value).isEqualTo(expected)
 * ```
 */
fun <T> LiveData<T>.getOrAwaitValue(
    timeoutSeconds: Long = 2,
    afterObserve: () -> Unit = {}
): T {
    var data: T? = null
    val latch = CountDownLatch(1)

    val observer = object : Observer<T> {
        override fun onChanged(value: T) {
            data = value
            latch.countDown()
            this@getOrAwaitValue.removeObserver(this)
        }
    }

    this.observeForever(observer)

    try {
        afterObserve.invoke()

        // Wait for the value
        if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
            throw TimeoutException("LiveData value was never set.")
        }
    } finally {
        this.removeObserver(observer)
    }

    @Suppress("UNCHECKED_CAST")
    return data as T
}

/**
 * Observes LiveData and returns a TestObserver for assertions.
 * The observer will collect all emitted values.
 *
 * Example:
 * ```
 * val observer = liveData.test()
 * // Trigger some action
 * observer.assertValue(expected)
 * ```
 */
fun <T> LiveData<T>.test(): TestObserver<T> {
    val observer = TestObserver<T>()
    observeForever(observer)
    return observer
}

/**
 * Observes LiveData temporarily and removes observer after block execution.
 * Useful for scoped observation in tests.
 *
 * Example:
 * ```
 * liveData.observeForTest { observer ->
 *     // Trigger action
 *     observer.assertValue(expected)
 * }
 * ```
 */
fun <T> LiveData<T>.observeForTest(block: (TestObserver<T>) -> Unit) {
    val observer = TestObserver<T>()
    try {
        observeForever(observer)
        block(observer)
    } finally {
        removeObserver(observer)
    }
}

/**
 * Waits for LiveData to emit a specific number of values.
 * Returns the list of all collected values.
 *
 * Example:
 * ```
 * val values = liveData.awaitValues(3)
 * assertThat(values).hasSize(3)
 * ```
 */
fun <T> LiveData<T>.awaitValues(
    count: Int,
    timeoutSeconds: Long = 2
): List<T> {
    val values = mutableListOf<T>()
    val latch = CountDownLatch(count)

    val observer = object : Observer<T> {
        override fun onChanged(value: T) {
            values.add(value)
            latch.countDown()
            if (values.size >= count) {
                this@awaitValues.removeObserver(this)
            }
        }
    }

    this.observeForever(observer)

    try {
        if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
            throw TimeoutException("LiveData did not emit $count values within $timeoutSeconds seconds. Got ${values.size} values.")
        }
    } finally {
        this.removeObserver(observer)
    }

    return values.toList()
}

/**
 * Waits for LiveData to emit a value that matches the predicate.
 * Returns the matching value.
 *
 * Example:
 * ```
 * val result = liveData.awaitValueMatching { it.status == "success" }
 * ```
 */
fun <T> LiveData<T>.awaitValueMatching(
    timeoutSeconds: Long = 2,
    predicate: (T) -> Boolean
): T {
    var result: T? = null
    val latch = CountDownLatch(1)

    val observer = object : Observer<T> {
        override fun onChanged(value: T) {
            if (predicate(value)) {
                result = value
                latch.countDown()
                this@awaitValueMatching.removeObserver(this)
            }
        }
    }

    this.observeForever(observer)

    try {
        if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
            throw TimeoutException("LiveData did not emit a matching value within $timeoutSeconds seconds.")
        }
    } finally {
        this.removeObserver(observer)
    }

    return result ?: throw AssertionError("No matching value was emitted")
}

/**
 * Asserts that LiveData has no value or is null.
 *
 * Example:
 * ```
 * liveData.assertNoValue()
 * ```
 */
fun <T> LiveData<T>.assertNoValue() {
    if (this.value != null) {
        throw AssertionError("Expected no value but got ${this.value}")
    }
}

/**
 * Asserts that LiveData's current value equals the expected value.
 *
 * Example:
 * ```
 * liveData.assertValue(expectedValue)
 * ```
 */
fun <T> LiveData<T>.assertValue(expected: T) {
    val actual = this.value
    if (actual != expected) {
        throw AssertionError("Expected $expected but got $actual")
    }
}

/**
 * Asserts that LiveData's current value matches the predicate.
 *
 * Example:
 * ```
 * liveData.assertValueMatches { it.id == "test" }
 * ```
 */
fun <T> LiveData<T>.assertValueMatches(predicate: (T?) -> Boolean) {
    if (!predicate(this.value)) {
        throw AssertionError("LiveData value ${this.value} does not match predicate")
    }
}

/**
 * Collects all values emitted by LiveData within a timeout period.
 * Useful for collecting multiple emissions.
 *
 * Example:
 * ```
 * val values = liveData.collectValues(timeoutSeconds = 3)
 * assertThat(values).containsExactly(value1, value2, value3)
 * ```
 */
fun <T> LiveData<T>.collectValues(
    timeoutSeconds: Long = 2
): List<T> {
    val values = mutableListOf<T>()
    val observer = object : Observer<T> {
        override fun onChanged(value: T) {
            values.add(value)
        }
    }

    this.observeForever(observer)

    try {
        Thread.sleep(timeoutSeconds * 1000)
    } finally {
        this.removeObserver(observer)
    }

    return values.toList()
}

/**
 * Waits until LiveData emits any value (ignoring the actual value).
 * Useful for waiting for state changes.
 *
 * Example:
 * ```
 * liveData.awaitAnyValue()
 * // Now proceed with test
 * ```
 */
fun <T> LiveData<T>.awaitAnyValue(timeoutSeconds: Long = 2) {
    val latch = CountDownLatch(1)

    val observer = object : Observer<T> {
        override fun onChanged(value: T) {
            latch.countDown()
            this@awaitAnyValue.removeObserver(this)
        }
    }

    this.observeForever(observer)

    try {
        if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
            throw TimeoutException("LiveData did not emit any value within $timeoutSeconds seconds.")
        }
    } finally {
        this.removeObserver(observer)
    }
}

/**
 * Blocks until LiveData emits a non-null value.
 * Returns the value once emitted.
 *
 * Example:
 * ```
 * val result = liveData.awaitNonNullValue()
 * ```
 */
fun <T : Any> LiveData<T?>.awaitNonNullValue(timeoutSeconds: Long = 2): T {
    var result: T? = null
    val latch = CountDownLatch(1)

    val observer = object : Observer<T?> {
        override fun onChanged(value: T?) {
            if (value != null) {
                result = value
                latch.countDown()
                this@awaitNonNullValue.removeObserver(this)
            }
        }
    }

    this.observeForever(observer)

    try {
        if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
            throw TimeoutException("LiveData did not emit a non-null value within $timeoutSeconds seconds.")
        }
    } finally {
        this.removeObserver(observer)
    }

    return result ?: throw AssertionError("No non-null value was emitted")
}

/**
 * Creates a mock observer for LiveData.
 * Useful when you just need to start observing but don't care about values.
 *
 * Example:
 * ```
 * val observer = liveData.mockObserver()
 * // Trigger action
 * liveData.removeObserver(observer)
 * ```
 */
fun <T> LiveData<T>.mockObserver(): Observer<T> {
    val observer = Observer<T> { }
    observeForever(observer)
    return observer
}

/**
 * Asserts that LiveData has emitted at least one value.
 *
 * Example:
 * ```
 * liveData.assertHasValue()
 * ```
 */
fun <T> LiveData<T>.assertHasValue() {
    if (this.value == null) {
        throw AssertionError("Expected LiveData to have a value but it was null")
    }
}
