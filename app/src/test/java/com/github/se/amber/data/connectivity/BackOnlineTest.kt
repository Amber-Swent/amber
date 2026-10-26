// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.connectivity

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Checks [backOnline] on a state the test changes, in virtual time: when it emits, and the
 * connection changes its debounce ignores.
 */
@OptIn(ExperimentalCoroutinesApi::class) // runCurrent, advanceTimeBy
class BackOnlineTest {

  /** The app's wait, so the tests check the value the app uses. */
  private val debounce = BACK_ONLINE_DEBOUNCE_MILLIS

  /**
   * Starts collecting [backOnline] of [online] in the background, until the test ends. Returns a
   * function reading how many times it has emitted so far.
   */
  private fun TestScope.collectBackOnline(online: MutableStateFlow<Boolean>): () -> Int {
    var count = 0
    backgroundScope.launch { online.backOnline(debounce).collect { count++ } }
    runCurrent()
    return { count }
  }

  @Test
  fun startingOnlineEmitsNothing() = runTest {
    val online = MutableStateFlow(true)
    val emitted = collectBackOnline(online)

    advanceTimeBy((10 * debounce).milliseconds)

    assertEquals(0, emitted())
  }

  @Test
  fun startingOfflineEmitsNothing() = runTest {
    val online = MutableStateFlow(false)
    val emitted = collectBackOnline(online)

    advanceTimeBy((10 * debounce).milliseconds)

    assertEquals(0, emitted())
  }

  @Test
  fun comingBackEmitsOnceAfterTheDebounce() = runTest {
    val online = MutableStateFlow(false)
    val emitted = collectBackOnline(online)

    online.value = true
    advanceTimeBy((debounce - 1).milliseconds)
    runCurrent()
    assertEquals(0, emitted())

    advanceTimeBy(2.milliseconds)
    assertEquals(1, emitted())

    advanceTimeBy((10 * debounce).milliseconds)
    assertEquals(1, emitted())
  }

  @Test
  fun comingBackSoonAfterTheStartStillEmits() = runTest {
    // The state at the start counts, even if it changes before the debounce would report it.
    val online = MutableStateFlow(false)
    val emitted = collectBackOnline(online)

    advanceTimeBy((debounce / 2).milliseconds)
    online.value = true
    advanceTimeBy((debounce + 1).milliseconds)

    assertEquals(1, emitted())
  }

  @Test
  fun aConnectionLastingLessThanTheDebounceEmitsNothing() = runTest {
    val online = MutableStateFlow(false)
    val emitted = collectBackOnline(online)

    online.value = true
    advanceTimeBy((debounce / 2).milliseconds)
    online.value = false
    advanceTimeBy((10 * debounce).milliseconds)

    assertEquals(0, emitted())
  }

  @Test
  fun losingTheConnectionForLessThanTheDebounceEmitsNothing() = runTest {
    // e.g. switching from Wi-Fi to mobile data
    val online = MutableStateFlow(true)
    val emitted = collectBackOnline(online)

    online.value = false
    advanceTimeBy((debounce / 2).milliseconds)
    online.value = true
    advanceTimeBy((10 * debounce).milliseconds)

    assertEquals(0, emitted())
  }

  @Test
  fun emitsEachTimeTheConnectionComesBack() = runTest {
    val online = MutableStateFlow(true)
    val emitted = collectBackOnline(online)

    repeat(3) {
      online.value = false
      advanceTimeBy((debounce + 1).milliseconds)
      online.value = true
      advanceTimeBy((debounce + 1).milliseconds)
    }

    assertEquals(3, emitted())
  }

  @Test
  fun eachCollectorStartsFromTheStateItSees() = runTest {
    val online = MutableStateFlow(false)
    val first = collectBackOnline(online)
    online.value = true
    advanceTimeBy((debounce + 1).milliseconds)

    // starts while online: the comeback the first collector saw isn't its own
    val second = collectBackOnline(online)
    advanceTimeBy((10 * debounce).milliseconds)
    online.value = false
    advanceTimeBy((debounce + 1).milliseconds)
    online.value = true
    advanceTimeBy((debounce + 1).milliseconds)

    assertEquals(2, first())
    assertEquals(1, second())
  }
}
