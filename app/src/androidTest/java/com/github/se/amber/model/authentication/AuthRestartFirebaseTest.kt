// Written by Alaabrine, with assistance from Cursor.

package com.github.se.amber.model.authentication

import androidx.test.platform.app.InstrumentationRegistry
import com.github.se.amber.e2e.FirebaseEmulator
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.auth
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

private const val APP_NAME = "restart"

/**
 * A new process reads the signed-in user at once, the way a send does. The user the previous
 * process saved has to be there already: Auth loads it while the app is created.
 */
class AuthRestartFirebaseTest {
  private fun <T> Task<T>.await(): T = Tasks.await(this, 20, TimeUnit.SECONDS)

  @Test
  fun aRestartedAppStillHasTheSignedInUserRightAway() {
    FirebaseEmulator.requireOrSkip(setOf(FirebaseEmulator.Service.AUTH))
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    // The same fake options as FirebaseEmulator: Auth refuses a key that isn't well formed.
    val options =
        FirebaseOptions.Builder()
            .setProjectId(FirebaseEmulator.PROJECT_ID)
            .setApplicationId("1:000000000000:android:0000000000000000")
            .setApiKey("AIzaSyD-amber-emulator-only-fake-key-00")
            .build()
    val first = FirebaseApp.initializeApp(context, options, APP_NAME)
    val auth = Firebase.auth(first)
    auth.useEmulator(FirebaseEmulator.HOST, FirebaseEmulator.AUTH_PORT)
    val email = "restart-${System.currentTimeMillis()}@amber.test"
    val uid = checkNotNull(auth.createUserWithEmailAndPassword(email, "password").await().user).uid

    first.delete()
    val again = FirebaseApp.initializeApp(context, options, APP_NAME)
    try {
      assertEquals(uid, Firebase.auth(again).currentUser?.uid)
    } finally {
      again.delete()
    }
  }
}
