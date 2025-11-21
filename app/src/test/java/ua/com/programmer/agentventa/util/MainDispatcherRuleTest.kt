package ua.com.programmer.agentventa.util

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * Example tests demonstrating the usage of MainDispatcherRule.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRuleTest {

    /**
     * Example 1: Using the default UnconfinedTestDispatcher
     * This executes coroutines eagerly without delay.
     */
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `coroutines using Main dispatcher run successfully with UnconfinedTestDispatcher`() = runTest {
        var result = ""

        // Launch a coroutine on Dispatchers.Main
        launch(Dispatchers.Main) {
            result = "Coroutine executed"
        }

        // With UnconfinedTestDispatcher, the coroutine runs immediately
        assertThat(result).isEqualTo("Coroutine executed")
    }

    @Test
    fun `multiple coroutines on Main dispatcher execute correctly`() = runTest {
        val results = mutableListOf<String>()

        launch(Dispatchers.Main) {
            results.add("First")
        }

        launch(Dispatchers.Main) {
            results.add("Second")
        }

        assertThat(results).containsExactly("First", "Second")
    }
}

/**
 * Example tests demonstrating StandardTestDispatcher usage.
 * This dispatcher requires manual advancement of virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRuleWithStandardDispatcherTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    @Test
    fun `coroutines with delays can be controlled with StandardTestDispatcher`() = runTest(testDispatcher) {
        var result = ""

        launch(Dispatchers.Main) {
            delay(1000)
            result = "Delayed execution"
        }

        // Result is not yet set because we haven't advanced time
        assertThat(result).isEmpty()

        // Advance time until all coroutines complete
        advanceUntilIdle()

        assertThat(result).isEqualTo("Delayed execution")
    }
}
