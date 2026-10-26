// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowConnectivityManager
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * Checks [AndroidConnectivityMonitor] against Robolectric's connectivity manager. The test plays
 * Android: it sets the current network, then calls the registered callback as Android would.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class) // runCurrent, advanceTimeBy
class AndroidConnectivityMonitorTest {

  /** The app's connectivity manager, which Robolectric replaces with its own. */
  private val manager =
      ApplicationProvider.getApplicationContext<Context>()
          .getSystemService(ConnectivityManager::class.java)
  /** Robolectric's side of [manager]: sets the networks and lists the registered callbacks. */
  private val shadow: ShadowConnectivityManager
    get() = shadowOf(manager)

  /** Robolectric's current network, there from the start. Plays the Wi-Fi in the tests. */
  private val wifi: Network
    get() = manager.activeNetwork!!

  /** Another network, e.g. mobile data taking over from [wifi]; only known to the callback. */
  private val mobile: Network = ShadowNetwork.newInstance(2)

  /** Leaves the phone with no current network. */
  private fun noNetwork() = shadow.setActiveNetworkInfo(null)

  /** Network capabilities holding exactly [capabilities] (`NET_CAPABILITY_*` constants). */
  private fun capabilities(vararg capabilities: Int): NetworkCapabilities =
      ShadowNetworkCapabilities.newInstance().also { result ->
        capabilities.forEach { shadowOf(result).addCapability(it) }
      }

  /** The capabilities of a network that reaches the internet: it says so and Android checked. */
  private val validated
    get() =
        capabilities(
            NetworkCapabilities.NET_CAPABILITY_INTERNET,
            NetworkCapabilities.NET_CAPABILITY_VALIDATED,
        )

  /** Gives the current network [networkCapabilities], before the monitor exists. */
  private fun currentNetwork(networkCapabilities: NetworkCapabilities) =
      shadow.setNetworkCapabilities(wifi, networkCapabilities)

  /** The one callback the monitor registered, to play Android's part; fails if there are more. */
  private fun callback(): ConnectivityManager.NetworkCallback = shadow.networkCallbacks.single()

  /**
   * A monitor watching in a child of [job] (by default, the test's background work, cancelled when
   * the test ends), on the test's virtual time. Lets its callback register, then plays what Android
   * does on registering and Robolectric doesn't: reporting the current network, if any, to it.
   */
  private fun TestScope.monitor(job: Job = backgroundScope.coroutineContext[Job]!!) =
      monitorOf(manager, job).also {
        runCurrent()
        val current = manager.activeNetwork ?: return@also
        val currentCapabilities = manager.getNetworkCapabilities(current) ?: return@also
        callback().onCapabilitiesChanged(current, currentCapabilities)
        runCurrent()
      }

  /**
   * A monitor of [connectivityManager] watching in a child of [job], on the test's virtual time.
   * Lets its callback register before returning; plays nothing else.
   */
  private fun TestScope.monitorOf(
      connectivityManager: ConnectivityManager,
      job: Job = backgroundScope.coroutineContext[Job]!!,
  ) =
      AndroidConnectivityMonitor(
              connectivityManager,
              CoroutineScope(StandardTestDispatcher(testScheduler) + Job(job)),
          )
          .also { runCurrent() }

  /**
   * A connectivity manager whose current network is [network] with [capabilities] until it
   * registers a callback, then whatever [onRegister] sets. [onRegister] plays what happens while
   * registering; it gets the callback.
   */
  private fun managerChangingOnRegister(
      network: Network,
      capabilities: NetworkCapabilities,
      onRegister: (ConnectivityManager.NetworkCallback) -> NetworkCapabilities?,
  ): ConnectivityManager {
    var current: NetworkCapabilities? = capabilities
    return mockk(relaxed = true) {
      every { activeNetwork } answers { if (current == null) null else network }
      every { getNetworkCapabilities(any()) } answers { current }
      every { registerDefaultNetworkCallback(any<ConnectivityManager.NetworkCallback>()) } answers
          {
            current = onRegister(firstArg())
          }
    }
  }

  @Test
  fun noticesANetworkLostWhileRegistering() = runTest {
    // online when the monitor is built, lost before Android has the callback: Android then has no
    // default network to report, so only reading the state after registering notices it
    val connectivityManager = managerChangingOnRegister(wifi, validated) { null }

    assertFalse(monitorOf(connectivityManager).isOnline.value)
  }

  @Test
  fun aReportOnRegisteringIsNotReplacedByTheReading() = runTest {
    // Android reports the network as not reaching the internet while registering, but a reading
    // would still say it does: the report is the newer state
    val connectivityManager =
        managerChangingOnRegister(wifi, validated) { callback ->
          callback.onCapabilitiesChanged(
              wifi,
              capabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET),
          )
          validated
        }

    assertFalse(monitorOf(connectivityManager).isOnline.value)
  }

  @Test
  fun startsOfflineWithNoNetwork() = runTest {
    noNetwork()

    assertFalse(monitor().isOnline.value)
  }

  @Test
  fun startsOnlineOnAValidatedNetwork() = runTest {
    currentNetwork(validated)

    assertTrue(monitor().isOnline.value)
  }

  @Test
  fun aNetworkNotCheckedToReachTheInternetIsOffline() = runTest {
    // e.g. a Wi-Fi asking to log in on a web page
    currentNetwork(capabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET))

    assertFalse(monitor().isOnline.value)
  }

  @Test
  fun goesOnlineOnceTheNetworkIsChecked() = runTest {
    val network = wifi
    noNetwork()
    val monitor = monitor()

    callback()
        .onCapabilitiesChanged(network, capabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET))
    runCurrent()
    assertFalse(monitor.isOnline.value)

    callback().onCapabilitiesChanged(network, validated)
    runCurrent()
    assertTrue(monitor.isOnline.value)
  }

  @Test
  fun goesOfflineWhenTheNetworkIsLost() = runTest {
    currentNetwork(validated)
    val monitor = monitor()

    callback().onLost(wifi)
    runCurrent()

    assertFalse(monitor.isOnline.value)
  }

  @Test
  fun ignoresTheLossOfANetworkNoLongerTheDefault() = runTest {
    // Wi-Fi gives way to mobile data, and Android reports Wi-Fi lost only afterwards
    currentNetwork(validated)
    val monitor = monitor()

    callback().onCapabilitiesChanged(mobile, validated)
    callback().onLost(wifi)
    runCurrent()

    assertTrue(monitor.isOnline.value)
  }

  @Test
  fun usesOneCallbackHoweverManyCollect() = runTest {
    val monitor = monitor()
    repeat(5) {
      backgroundScope.launch { monitor.isOnline.collect {} }
      backgroundScope.launch { monitor.backOnline.collect {} }
    }
    runCurrent()

    assertEquals(1, shadow.networkCallbacks.size)
  }

  @Test
  fun stopsWatchingWhenItsScopeIsCancelled() = runTest {
    val job = Job()
    monitor(job)
    assertEquals(1, shadow.networkCallbacks.size)

    job.cancel()
    runCurrent()

    assertTrue(shadow.networkCallbacks.isEmpty())
  }

  @Test
  fun backOnlineEmitsWhenTheConnectionComesBack() = runTest {
    currentNetwork(validated)
    val monitor = monitor()
    var count = 0
    backgroundScope.launch { monitor.backOnline.collect { count++ } }
    runCurrent()

    callback().onLost(wifi)
    advanceTimeBy((BACK_ONLINE_DEBOUNCE_MILLIS + 1).milliseconds)
    callback().onCapabilitiesChanged(wifi, validated)
    advanceTimeBy((BACK_ONLINE_DEBOUNCE_MILLIS + 1).milliseconds)

    assertEquals(1, count)
  }
}
