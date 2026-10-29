// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.connectivity

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

/**
 * A [ConnectivityMonitor] following Android's default network, the one the app's connections use.
 *
 * It watches with a single network callback, registered when it is built and kept until [scope] is
 * cancelled, however many screens collect [isOnline] or [backOnline]: reading [isOnline]'s value is
 * then always up to date, even with no collector.
 *
 * @param connectivityManager the system's, from `Context.getSystemService`.
 * @param scope keeps the callback registered; the app's lives as long as the process.
 */
class AndroidConnectivityMonitor(
    private val connectivityManager: ConnectivityManager,
    scope: CoroutineScope,
) : ConnectivityMonitor {

  /**
   * Starts from the current state, read when the monitor is built, then follows the callback. The
   * callback stays registered even with no collector ([SharingStarted.Eagerly]), so its value is
   * always up to date.
   */
  override val isOnline: StateFlow<Boolean> =
      defaultNetworkOnline().stateIn(scope, SharingStarted.Eagerly, currentlyOnline())

  /** [isOnline]'s comebacks, waiting [BACK_ONLINE_DEBOUNCE_MILLIS]; uses no other callback. */
  override val backOnline: Flow<Unit> = isOnline.backOnline()

  /**
   * Whether the default network reaches the internet: the current state, then each change Android
   * reports. Registers a network callback when collected, and unregisters it when the collection
   * stops. A new default network (e.g. Wi-Fi replacing mobile data) is reported through
   * [ConnectivityManager.NetworkCallback.onCapabilitiesChanged], so it needs no case of its own;
   * only the loss of the current default network counts. Values may repeat; the [StateFlow] it
   * feeds drops the repeats.
   *
   * The current state is read after registering, and sent only if the callback hasn't reported
   * anything yet: read before, a network lost in between would never be reported (there is then no
   * default network to report); sent after a report, it could replace that newer state.
   */
  private fun defaultNetworkOnline(): Flow<Boolean> = callbackFlow {
    // Makes "has the callback reported yet?" and sending one step, so the reading can't come
    // between a report and its send.
    val lock = Any()
    var reported = false
    fun report(online: Boolean) =
        synchronized(lock) {
          reported = true
          trySend(online)
        }

    val callback =
        object : ConnectivityManager.NetworkCallback() {
          /**
           * The default network Android last reported, null once lost. Only used on Android's
           * connectivity thread, which calls this callback's methods one at a time.
           */
          private var defaultNetwork: Network? = null

          // Android calls it right after a network becomes the default, then each time what it
          // can do changes (for example once it is checked to reach the internet).
          override fun onCapabilitiesChanged(
              network: Network,
              networkCapabilities: NetworkCapabilities,
          ) {
            defaultNetwork = network
            report(networkCapabilities.reachesInternet())
          }

          // Android may report that the previous default network lost that status after the new
          // one was reported (the order isn't documented, e.g. when Wi-Fi gives way to mobile
          // data): going offline then would last until the next change.
          override fun onLost(network: Network) {
            if (network == defaultNetwork) {
              defaultNetwork = null
              report(false)
            }
          }
        }
    connectivityManager.registerDefaultNetworkCallback(callback)
    // With no default network, Android calls nothing on registering: start from the current state.
    synchronized(lock) { if (!reported) trySend(currentlyOnline()) }
    awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
  }

  /** Whether the default network reaches the internet right now; false if there is none. */
  private fun currentlyOnline(): Boolean =
      connectivityManager
          .getNetworkCapabilities(connectivityManager.activeNetwork)
          ?.reachesInternet() ?: false

  /**
   * Whether a network with these capabilities reaches the internet: it says it can
   * ([NetworkCapabilities.NET_CAPABILITY_INTERNET]) and Android checked that it does
   * ([NetworkCapabilities.NET_CAPABILITY_VALIDATED]). A Wi-Fi whose login page isn't filled in, or
   * whose internet access is down, has only the first.
   */
  private fun NetworkCapabilities.reachesInternet(): Boolean =
      hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
          hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
