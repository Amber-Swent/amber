// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.e2e

import androidx.test.core.app.ApplicationProvider
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.app
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.memoryCacheSettings
import com.google.firebase.functions.functions
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Points the whole app at the local Auth, Firestore and Functions emulators, for end-to-end tests
 * that drive the real screens against the real backend (security rules and `redeemInvitation`).
 *
 * [useForDefaultApp] replaces the default Firebase app, which every repository uses, with one on
 * the fake [PROJECT_ID]: Firebase gives `demo-` projects no real resources, so the tests never
 * reach the real project. One request does leave the device: Functions (which requires Instance ID)
 * asks the real Firebase Installations server for an id, sending only these fake options. Google
 * rejects the fake key, and the calls go on without the id.
 *
 * Start the emulators on the host before running the tests (`functions/` needs `npm ci` first):
 * `firebase emulators:start --project demo-amber --only auth,firestore,functions`
 *
 * The bucket trigger skips itself there, since the emulators can't create buckets. Seen from the
 * Android emulator, the host is 10.0.2.2.
 */
object FirebaseEmulator {
  const val PROJECT_ID = "demo-amber"

  private const val HOST = "10.0.2.2"
  private const val AUTH_PORT = 9099
  private const val FIRESTORE_PORT = 8080
  private const val FUNCTIONS_PORT = 5001
  private const val CONNECT_TIMEOUT_MILLIS = 1_000

  /**
   * Replaces the default Firebase app with a [PROJECT_ID] one wired to the emulators.
   *
   * Call it before anything uses Firebase, so before the app's content is set: an instance created
   * earlier would keep the real project. The test orchestrator runs each test in a fresh process,
   * where nothing has used Firebase yet.
   */
  fun useForDefaultApp() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    FirebaseApp.getApps(context).forEach { it.delete() }
    val options =
        FirebaseOptions.Builder()
            .setProjectId(PROJECT_ID)
            .setApplicationId("1:000000000000:android:0000000000000000")
            // Auth refused "demo-api-key" even for the emulator; this well-formed key is fake
            .setApiKey("AIzaSyD-amber-emulator-only-fake-key-00")
            .build()
    FirebaseApp.initializeApp(context, options)

    // useEmulator throws once an instance has been used, so wire all three before anything else
    Firebase.auth.useEmulator(HOST, AUTH_PORT)
    Firebase.firestore.apply {
      // no disk cache, so a run never reads documents left by an earlier one
      firestoreSettings = firestoreSettings { setLocalCacheSettings(memoryCacheSettings {}) }
      useEmulator(HOST, FIRESTORE_PORT)
    }
    Firebase.functions.useEmulator(HOST, FUNCTIONS_PORT)

    check(Firebase.app.options.projectId == PROJECT_ID) { "The default app is not the demo one" }
  }

  /** Whether all three emulators accept connections. */
  fun isRunning(): Boolean = listOf(AUTH_PORT, FIRESTORE_PORT, FUNCTIONS_PORT).all(::accepts)

  private fun accepts(port: Int): Boolean =
      try {
        Socket().use { it.connect(InetSocketAddress(HOST, port), CONNECT_TIMEOUT_MILLIS) }
        true
      } catch (_: IOException) {
        false
      }
}
