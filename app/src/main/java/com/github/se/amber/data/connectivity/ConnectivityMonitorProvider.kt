// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.connectivity

import android.net.ConnectivityManager
import com.google.firebase.Firebase
import com.google.firebase.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Holds the app's single [ConnectivityMonitor], so the whole app shares one network callback.
 *
 * ViewModels should receive the monitor as a constructor parameter (their factory reads it here),
 * so tests can pass a fake instead.
 */
object ConnectivityMonitorProvider {

  /** Keeps the monitor's network callback registered, for as long as the process lives. */
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  /**
   * The app's monitor, built on first use. The app's context comes from Firebase, which the app
   * initializes before any activity or background job starts, as for the repositories; if building
   * fails (Firebase not initialized yet), the next use tries again.
   */
  val monitor: ConnectivityMonitor by lazy {
    val context = Firebase.app.applicationContext
    AndroidConnectivityMonitor(
        connectivityManager = context.getSystemService(ConnectivityManager::class.java),
        scope = scope,
    )
  }
}
