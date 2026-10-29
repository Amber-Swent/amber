// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.connectivity

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A [ConnectivityMonitor] whose connection the test sets through [isOnline]. Its [backOnline] is
 * the app's, waiting [debounceMillis] (none by default, so tests needn't advance time).
 */
class FakeConnectivityMonitor(online: Boolean = true, debounceMillis: Long = 0L) :
    ConnectivityMonitor {

  override val isOnline = MutableStateFlow(online)

  override val backOnline: Flow<Unit> = isOnline.backOnline(debounceMillis)
}
