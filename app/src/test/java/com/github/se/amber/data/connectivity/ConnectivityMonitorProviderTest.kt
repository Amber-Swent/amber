// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.connectivity

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Checks how [ConnectivityMonitorProvider] builds the monitor, on Robolectric, where Firebase
 * (which gives it the app's context) is initialized by hand like the app does at startup. Nothing
 * here contacts Firebase.
 */
@RunWith(RobolectricTestRunner::class)
class ConnectivityMonitorProviderTest {

  private val context: Context = RuntimeEnvironment.getApplication()

  @Before
  fun initializeFirebase() {
    if (FirebaseApp.getApps(context).isEmpty()) {
      FirebaseApp.initializeApp(
          context,
          FirebaseOptions.Builder()
              .setApplicationId("1:123456789012:android:abcdef123456")
              .setApiKey("test-api-key")
              .setProjectId("amber-test")
              .build(),
      )
    }
  }

  @Test
  fun monitorIsASingleAndroidInstance() {
    val monitor = ConnectivityMonitorProvider.monitor

    assertTrue(monitor is AndroidConnectivityMonitor)
    // the whole app must share its one network callback
    assertSame(monitor, ConnectivityMonitorProvider.monitor)
  }
}
