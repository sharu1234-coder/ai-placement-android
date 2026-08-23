package com.aiplacementos.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * ViewModels use `viewModelScope`, which dispatches on `Dispatchers.Main` -- unavailable in
 * a plain JVM unit test (no real Android Looper). This rule swaps it for an
 * UnconfinedTestDispatcher for the duration of each test, so `viewModelScope.launch { ... }`
 * runs eagerly and synchronously (no manual `advanceUntilIdle()` needed for these
 * ViewModels' simple linear "loading -> await repository call -> success/error" bodies).
 *
 * @OptIn (not @ExperimentalCoroutinesApi on the class) so this opt-in is contained here --
 * test classes using this rule don't need to opt in themselves.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
