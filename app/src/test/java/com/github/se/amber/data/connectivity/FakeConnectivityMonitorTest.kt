// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.connectivity

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks what tests using [FakeConnectivityMonitor] rely on: it starts with the state it is given,
 * and by default its [FakeConnectivityMonitor.backOnline] emits without the test advancing time.
 */
@OptIn(ExperimentalCoroutinesApi::class) // runCurrent, advanceTimeBy
class FakeConnectivityMonitorTest {

  /**
   * Starts collecting [fake]'s [FakeConnectivityMonitor.backOnline] in the background, until the
   * test ends. Returns a function reading how many times it has emitted so far.
   */
  private fun TestScope.collectBackOnline(fake: FakeConnectivityMonitor): () -> Int {
    var count = 0
    backgroundScope.launch { fake.backOnline.collect { count++ } }
    runCurrent()
    return { count }
  }

  @Test
  fun startsWithTheGivenState() {
    assertTrue(FakeConnectivityMonitor().isOnline.value)
    assertFalse(FakeConnectivityMonitor(online = false).isOnline.value)
  }

  @Test
  fun emitsWithoutWaitingByDefault() = runTest {
    val fake = FakeConnectivityMonitor(online = false)
    val emitted = collectBackOnline(fake)

    fake.isOnline.value = true
    runCurrent()

    assertEquals(1, emitted())
  }

  @Test
  fun waitsTheDebounceItIsGiven() = runTest {
    val fake = FakeConnectivityMonitor(online = false, debounceMillis = 500L)
    val emitted = collectBackOnline(fake)

    fake.isOnline.value = true
    advanceTimeBy(499.milliseconds)
    runCurrent()
    assertEquals(0, emitted())

    advanceTimeBy(2.milliseconds)
    assertEquals(1, emitted())
  }
}
