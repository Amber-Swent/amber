// Written by Neha Chakraborty, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.account

import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Checks the real Firebase pieces that the unit tests of account deletion replace with fakes. */
class AccountDeletionFirebaseTest {
  private fun <T> Task<T>.await(): T = Tasks.await(this, 20, TimeUnit.SECONDS)

  @Test
  fun theDefaultRepositoryCanBeBuilt() {
    assertNotNull(AccountDeletionRepositoryProvider.default.repository)
  }

  @Test
  fun stoppingAndClearingFirestoreRemovesItsLocalCopy() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val options =
        FirebaseOptions.Builder()
            .setProjectId("demo-amber")
            .setApplicationId("1:1:android:1")
            .setApiKey("fake-api-key")
            .build()
    val app = FirebaseApp.initializeApp(context, options, "account-deletion-test")
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
