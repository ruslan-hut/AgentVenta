package ua.com.programmer.agentventa.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit rule that sets the Main dispatcher to a TestDispatcher for testing.
 *
 * This rule replaces Dispatchers.Main with a TestDispatcher, which allows
 * coroutines that use the Main dispatcher to run in tests without requiring
 * an Android runtime.
 *
 * Usage:
 * ```
 * @get:Rule
 * val mainDispatcherRule = MainDispatcherRule()
 * ```
 *
 * Or with a custom dispatcher:
 * ```
 * @get:Rule
 * val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())
 * ```
 *
 * @param testDispatcher The TestDispatcher to use. Defaults to UnconfinedTestDispatcher.
 */
@ExperimentalCoroutinesApi
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {

    /**
     * Called before each test. Sets the Main dispatcher to the test dispatcher.
     */
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    /**
     * Called after each test. Resets the Main dispatcher to the original.
     */
    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
