// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.amber.data.circle.FirebaseCareCircleRepository
import com.github.se.amber.data.onboarding.FirebaseOnboardingRepository
import com.github.se.amber.data.profile.FirebaseProfileRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.app
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks [EmulatorAdmin] against the Auth and Firestore emulators: each test sets up what a real
 * caregiver would have, through the app's repositories, then runs one admin call on it.
 *
 * The bucket cleanup ([EmulatorAdmin.deleteBucketFiles]) needs files a caregiver uploaded, so the
 * picture upload end-to-end test covers it instead.
 */
@RunWith(AndroidJUnit4::class)
class EmulatorAdminTest {
  private val email = "admin-${UUID.randomUUID()}@amber.test"
  private lateinit var uid: String
  private lateinit var circle: CareCircle

  /** Signs a new caregiver in on the emulators and creates their profile and circle. */
  @Before
  fun createCaregiverWithCircle() = runBlocking {
    FirebaseEmulator.requireOrSkip(
        setOf(FirebaseEmulator.Service.AUTH, FirebaseEmulator.Service.FIRESTORE)
    )
    FirebaseEmulator.useForDefaultApp()
    uid =
        checkNotNull(Firebase.auth.createUserWithEmailAndPassword(email, PASSWORD).await().user).uid
    FirebaseProfileRepository()
        .createProfile(Person(firstName = "Ada"), Role.CAREGIVER)
        .getOrThrow()
    circle = FirebaseOnboardingRepository().createInitialCareCircle("Family").getOrThrow()
  }

  /** Removes what [createCaregiverWithCircle] created; what is already gone is skipped. */
  @After
  fun cleanUp() {
    runCatching { EmulatorAdmin.deleteCircle(circle.id) }
    runCatching { EmulatorAdmin.deleteProfiles(listOf(uid)) }
    runCatching { EmulatorAdmin.deleteAccounts(listOf(uid)) }
  }

  @Test
  fun setStorageBucket_givesTheCircleItsBucketAndKeepsItsOtherFields() = runBlocking {
    EmulatorAdmin.setStorageBucket(circle.id, "demo-amber-bucket")

    val stored = FirebaseCareCircleRepository().getCircle(circle.id).getOrThrow()
    assertEquals(circle.copy(storageBucket = "demo-amber-bucket"), stored)
  }

  @Test
  fun deleteCircle_alsoDeletesItsStoriesAndAppointments() = runBlocking {
    val circleDocument = Firebase.firestore.collection("careCircles").document(circle.id)
    circleDocument.collection("stories").document("s1").set(mapOf("id" to "s1")).await()
    circleDocument.collection("appointments").document("a1").set(mapOf("id" to "a1")).await()

    EmulatorAdmin.deleteCircle(circle.id)

    assertTrue(EmulatorAdmin.documentNames("careCircles/${circle.id}/stories").isEmpty())
    assertTrue(EmulatorAdmin.documentNames("careCircles/${circle.id}/appointments").isEmpty())
    assertTrue(EmulatorAdmin.documentNames("careCircles").none { it.endsWith("/${circle.id}") })
  }

  @Test
  fun deleteAccounts_deletesTheAccountAndSkipsItOnceGone() =
      runBlocking<Unit> {
        Firebase.auth.signOut()

        EmulatorAdmin.deleteAccounts(listOf(uid))
        EmulatorAdmin.deleteAccounts(listOf(uid)) // already gone: no error

        assertThrows(FirebaseAuthInvalidUserException::class.java) {
          runBlocking { Firebase.auth.signInWithEmailAndPassword(email, PASSWORD).await() }
        }
      }

  @Test
  fun everyCall_refusesBeforeUseForDefaultApp() {
    cleanUp() // now, while the app is still on the emulators
    // the app's real project, as it is before useForDefaultApp() replaces it
    val realProject = FirebaseOptions.Builder(Firebase.app.options).setProjectId("amber").build()
    val context = ApplicationProvider.getApplicationContext<Context>()
    FirebaseApp.getApps(context).forEach { it.delete() }
    FirebaseApp.initializeApp(context, realProject)

    assertThrows(IllegalStateException::class.java) { EmulatorAdmin.deleteCircle(circle.id) }
  }

  private companion object {
    const val PASSWORD = "Password123!"
  }
}
