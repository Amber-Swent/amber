// Written by Neha Chakraborty, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.account

import androidx.test.platform.app.InstrumentationRegistry
import com.github.se.amber.e2e.FirebaseEmulator
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.app
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.functions.functions
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Checks the real Firebase pieces that the unit tests of account deletion replace with fakes. */
class AccountDeletionFirebaseTest {
  private fun <T> Task<T>.await(): T = Tasks.await(this, 20, TimeUnit.SECONDS)

  @Test
  fun theDefaultRepositoryCanBeBuilt() {
    assertNotNull(AccountDeletionRepositoryProvider.default.repository)
  }

  private fun firebaseApp(name: String): FirebaseApp =
      FirebaseApp.initializeApp(
          InstrumentationRegistry.getInstrumentation().targetContext,
          FirebaseOptions.Builder()
              .setProjectId("demo-amber")
              .setApplicationId("1:1:android:1")
              .setApiKey("fake-api-key")
              .build(),
          name,
      )

  @Test
  fun anInterruptedDeletionIsOnlyFinishedOnceTheAccountIsReallyGone() = runBlocking {
    val message = "Start the Firebase emulators: see FirebaseEmulator"
    val required =
        InstrumentationRegistry.getArguments().getString("requireFirebaseEmulators") == "true"
    if (required) assertTrue(message, FirebaseEmulator.isRunning())
    else assumeTrue(message, FirebaseEmulator.isRunning())
    FirebaseEmulator.useForDefaultApp()
    val phone = Firebase.auth
    // A second session of the same user, standing in for the backend deleting the account
    val backend =
        FirebaseAuth.getInstance(
            FirebaseApp.initializeApp(
                InstrumentationRegistry.getInstrumentation().targetContext,
                Firebase.app.options,
                "account-deletion-backend",
            ),
        )
    backend.useEmulator("10.0.2.2", 9099)
    val email = "gone-${System.currentTimeMillis()}@amber.test"
    phone.createUserWithEmailAndPassword(email, "password").await()
    backend.signInWithEmailAndPassword(email, "password").await()
    var clears = 0
    val marker =
        object : DeletionMarker {
          override val isSet = true

          override fun set() = Unit

          override fun remove() = Unit
        }
    val repository =
        FirebaseAccountDeletionRepository(
            { clears++ },
            marker,
            Firebase.functions,
            phone,
        )

    repository.finishInterruptedDeletion()
    assertEquals(0, clears)

    backend.currentUser!!.delete().await()
    repository.finishInterruptedDeletion()
    assertEquals(1, clears)
  }

  @Test
  fun stoppingAndClearingFirestoreRemovesItsLocalCopy() {
    val app = firebaseApp("account-deletion-test")
    val firestore = FirebaseFirestore.getInstance(app)
    firestore.disableNetwork().await()
    val document = firestore.document("users/gone")
    // doesn't complete offline, but is in the local cache straight away
    document.set(mapOf("uid" to "gone"))
    assertEquals("gone", document.get(Source.CACHE).await().get("uid"))

    firestore.terminate().await()
    firestore.clearPersistence().await()

    val reopened = FirebaseFirestore.getInstance(app)
    reopened.disableNetwork().await()
    val failure =
        assertThrows(ExecutionException::class.java) {
          reopened.document("users/gone").get(Source.CACHE).await()
        }
    assertEquals(
        FirebaseFirestoreException.Code.UNAVAILABLE,
        (failure.cause as FirebaseFirestoreException).code,
    )
  }
}
