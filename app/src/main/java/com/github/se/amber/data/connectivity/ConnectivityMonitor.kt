// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.connectivity

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow

/**
 * Watches whether the phone can reach the internet.
 *
 * It is a hint for the UI (an offline banner, retrying failed images, disabling an action that only
 * works online), never a guarantee: a call can still fail while [isOnline] is true, and must still
 * handle its errors. Sending media doesn't use it: WorkManager already waits for a connection, and
 * Firestore queues its writes offline.
 */
interface ConnectivityMonitor {

  /**
   * Whether the phone has a connection that Android checked reaches the internet. False on a
   * network that doesn't (a Wi-Fi login page, a dead connection). Starts with the current state.
   */
  val isOnline: StateFlow<Boolean>

  /**
   * Emits each time the connection comes back, once it has stayed back for
   * [BACK_ONLINE_DEBOUNCE_MILLIS] (see [StateFlow.backOnline]). Never emits for the state at
   * collection start.
   */
  val backOnline: Flow<Unit>
}

/**
 * How long the connection must stay back before [ConnectivityMonitor.backOnline] emits, and how
 * long it must stay lost to count as lost. Android may report a network a moment before Firebase
 * can be reached through it, and switching from Wi-Fi to mobile data can drop it for an instant.
 */
const val BACK_ONLINE_DEBOUNCE_MILLIS = 1_000L

/**
 * Emits when this online state goes from false to true and stays true for [debounceMillis]. A
 * change that is undone within [debounceMillis] (losing the connection for an instant, or getting
 * it for an instant) is ignored. The state at collection start counts as the previous state, so
 * collecting while online emits nothing until the connection is lost and comes back.
 *
 * Each collector watches on its own, from when it starts; collecting doesn't watch the network
 * again, it only reads this state.
 */
@OptIn(FlowPreview::class)
fun StateFlow<Boolean>.backOnline(debounceMillis: Long = BACK_ONLINE_DEBOUNCE_MILLIS): Flow<Unit> =
    flow {
      var wasOnline = value
      debounce(debounceMillis.milliseconds).collect { online ->
        if (online && !wasOnline) emit(Unit)
        wasOnline = online
      }
    }
