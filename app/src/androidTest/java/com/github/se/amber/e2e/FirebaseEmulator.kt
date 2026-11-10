// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Lou-Anne Maier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.e2e

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.app
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.memoryCacheSettings
import com.google.firebase.functions.functions
import com.google.firebase.storage.FirebaseStorage
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue

/**
 * Points the whole app at the local Auth, Firestore, Functions and Storage emulators, for
 * end-to-end tests that drive the real screens against the real backend (security rules,
 * `redeemInvitation` and the media files).
 *
 * The Firebase emulators are local copies of these Firebase services, started on the computer
 * running the tests. They apply the project's security rules and run its Cloud Functions, but keep
 * their data in memory, so a test never touches real users' data.
 *
 * [useForDefaultApp] replaces the default Firebase app, which every repository uses, with one on
 * the fake [PROJECT_ID]: Firebase gives `demo-` projects no real resources, so the tests never
 * reach the real project. One request does leave the device: Functions (which requires Instance ID)
 * asks the real Firebase Installations server for an id, sending only these fake options. Google
 * rejects the fake key, and the calls go on without the id.
 *
 * Start the emulators on the host before running the tests (`functions/` needs `npm ci` first):
 * `firebase emulators:start --project demo-amber --only auth,firestore,functions,storage`
 *
 * The bucket trigger skips itself there, since the emulators can't create buckets: a test that
 * stores files gives its circle a bucket itself ([EmulatorAdmin]) and wires it with
 * [useStorageFor]. Seen from the Android emulator, the host is 10.0.2.2.
 */
object FirebaseEmulator {
  /** The fake project the tests run on: Firebase gives a `demo-` project no real resources. */
  const val PROJECT_ID = "demo-amber"

  /** The host machine, seen from the Android emulator. */
  const val HOST = "10.0.2.2"
  /** The port of the Auth emulator, on [HOST]. */
  const val AUTH_PORT = 9099
  /** The port of the Firestore emulator, on [HOST]. */
  const val FIRESTORE_PORT = 8080
  /** The port of the Storage emulator, on [HOST]. */
  const val STORAGE_PORT = 9199
  /**
   * The instrumentation argument that makes missing emulators a failure instead of a skip. CI sets
   * it to `true`, since it starts the emulators: a missing one there is broken wiring.
   */
  private const val REQUIRE_EMULATORS_ARGUMENT = "requireFirebaseEmulators"
  private const val FUNCTIONS_PORT = 5001
  private const val CONNECT_TIMEOUT_MILLIS = 1_000
  /**
   * How long [requireOrSkip] keeps trying when the emulators are required. On a freshly booted
   * Android emulator, the first connections to [HOST] can fail for a few seconds although the
   * emulators are ready, as seen on CI.
   */
  private const val REQUIRED_WAIT_MILLIS = 15_000L
  private const val RETRY_DELAY_MILLIS = 500L

  /**
   * A Firebase emulator a test may need, with the port `firebase.json` gives it.
   *
   * @property port the port the emulator listens on, on [HOST]
   */
  enum class Service(val port: Int) {
    AUTH(AUTH_PORT),
    FIRESTORE(FIRESTORE_PORT),
    FUNCTIONS(FUNCTIONS_PORT),
    STORAGE(STORAGE_PORT),
  }

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
    Firebase.auth.signOut() // clears a user persisted by an earlier run
    Firebase.firestore.apply {
      // no disk cache, so a run never reads documents left by an earlier one
      firestoreSettings = firestoreSettings { setLocalCacheSettings(memoryCacheSettings {}) }
      useEmulator(HOST, FIRESTORE_PORT)
    }
    Firebase.functions.useEmulator(HOST, FUNCTIONS_PORT)

    check(Firebase.app.options.projectId == PROJECT_ID) { "The default app is not the demo one" }
  }

  /**
   * Points the Storage instance of [bucket] at the Storage emulator. The app gets that same
   * instance from `FirebaseStorage.getInstance("gs://<bucket>")` (see `CircleStorageProvider`), so
   * call it after [useForDefaultApp] and before the app touches the bucket: `useEmulator` throws
   * once the instance has been used.
   *
   * @param bucket the name of the bucket, without `gs://`, as stored in `CareCircle.storageBucket`
   */
  fun useStorageFor(bucket: String) {
    FirebaseStorage.getInstance("gs://$bucket").useEmulator(HOST, STORAGE_PORT)
  }

  /**
   * Makes the calling test fail if one of [services] isn't running and the
   * [REQUIRE_EMULATORS_ARGUMENT] instrumentation argument is `true`, or skips it otherwise. Call it
   * first, before [useForDefaultApp].
   *
   * When the emulators are required, it keeps trying for [REQUIRED_WAIT_MILLIS] before failing,
   * since the first connections from a freshly booted Android emulator can fail. Otherwise it tries
   * once, so a local run without emulators skips its tests without waiting.
   *
   * @param services the emulators the test needs; Auth, Firestore and Functions by default
   * @param message why the test can't run, shown when it fails or is skipped
   */
  fun requireOrSkip(
      services: Set<Service> = setOf(Service.AUTH, Service.FIRESTORE, Service.FUNCTIONS),
      message: String = "Firebase emulators are not running on the host: see FirebaseEmulator",
  ) {
    val required =
        InstrumentationRegistry.getArguments().getString(REQUIRE_EMULATORS_ARGUMENT) == "true"
    if (required) assertTrue(message, awaitRunning(services, REQUIRED_WAIT_MILLIS))
    else assumeTrue(message, isRunning(services))
  }

  /**
   * Waits until every one of [services] accepts connections, trying again every
   * [RETRY_DELAY_MILLIS].
   *
   * @param services the emulators the test needs
   * @param timeoutMillis how long to keep trying
   * @return true if all of them became reachable within [timeoutMillis]
   */
  private fun awaitRunning(services: Set<Service>, timeoutMillis: Long): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (!isRunning(services)) {
      if (System.currentTimeMillis() >= deadline) return false
      Thread.sleep(RETRY_DELAY_MILLIS)
    }
    return true
  }

  /**
   * Whether every one of [services] accepts connections.
   *
   * @param services the emulators the test needs; Auth, Firestore and Functions by default
   * @return true if all of them are reachable from the device
   */
  fun isRunning(
      services: Set<Service> = setOf(Service.AUTH, Service.FIRESTORE, Service.FUNCTIONS)
  ): Boolean = services.all { accepts(it.port) }

  /**
   * Whether something on [HOST] accepts a connection on [port].
   *
   * @param port the port to try
   * @return true if the connection was accepted within [CONNECT_TIMEOUT_MILLIS]
   */
  private fun accepts(port: Int): Boolean =
      try {
        Socket().use { it.connect(InetSocketAddress(HOST, port), CONNECT_TIMEOUT_MILLIS) }
        true
      } catch (_: IOException) {
        false
      }
}
