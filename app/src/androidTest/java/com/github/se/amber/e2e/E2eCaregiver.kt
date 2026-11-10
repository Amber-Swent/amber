// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.e2e

import android.util.Log
import com.github.se.amber.data.circle.FirebaseCareCircleRepository
import com.github.se.amber.data.onboarding.FirebaseOnboardingRepository
import com.github.se.amber.data.profile.FirebaseProfileRepository
import com.github.se.amber.e2e.FirebaseEmulator.Service
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.location.Place
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

/**
 * A signed-in caregiver with a care circle, ready for an upload test, on the Firebase emulators.
 *
 * [setUp] signs the caregiver in straight through Firebase Auth, with no sign-in screens. It then
 * creates her profile and circle through the app's repositories, as onboarding does. The circle
 * gets Tom, a person without an account, and Grandma's house, a saved place. The emulators can't
 * create the circle's storage bucket, so [EmulatorAdmin] records one, in place of the backend.
 * [cleanUp] deletes all of this, and only this.
 *
 * Call [setUp] from the test's `@Before`, before the app starts, and [cleanUp] from its `@After`.
 *
 * @param runId the test run's id, which makes every name and id of the run unique
 */
class E2eCaregiver(private val runId: String) {
  /** The circle's storage bucket on the Storage emulator. */
  val bucket = "demo-amber-e2e-$runId"

  /** The app's circle repository, built once [setUp] has pointed Firestore at the emulator. */
  val circleRepository by lazy { FirebaseCareCircleRepository() }

  /** The caregiver's uid. */
  lateinit var uid: String
    private set

  /** The id of the caregiver's circle. */
  lateinit var circleId: String
    private set

  /** Tom, a person of the circle without an account. */
  lateinit var tom: Person
    private set

  /** Grandma's house, one of the circle's saved places. */
  lateinit var savedPlace: Place
    private set

  /** The uids of the accounts [setUp] created, for [cleanUp] to delete them. */
  private val uids = mutableListOf<String>()

  /**
   * Checks the Auth, Firestore and Storage emulators, points the app at them, then creates the
   * signed-in caregiver, her circle, Tom, the saved place and the bucket.
   */
  fun setUp() {
    FirebaseEmulator.requireOrSkip(setOf(Service.AUTH, Service.FIRESTORE, Service.STORAGE))
    FirebaseEmulator.useForDefaultApp()
    FirebaseEmulator.useStorageFor(bucket)

    runBlocking {
      uid = signIn("ada-$runId@amber.test", NAME)
      FirebaseProfileRepository()
          .createProfile(Person(firstName = NAME, lastName = "Lovelace"), Role.CAREGIVER)
          .getOrThrow()
      circleId =
          FirebaseOnboardingRepository().createInitialCareCircle("E2E family").getOrThrow().id
      tom = circleRepository.addPerson(circleId, Person(firstName = PERSON_NAME)).getOrThrow()
      savedPlace = saveFrequentPlace()
    }
    EmulatorAdmin.setStorageBucket(circleId, bucket)
  }

  /**
   * Deletes what [setUp] created: the bucket's files, the circle and its subcollections, the
   * profile and the account. Each step runs even if an earlier one failed (see [runQuietly]).
   */
  fun cleanUp() {
    if (uids.isEmpty()) return // skipped before anything was created
    runQuietly("the bucket's files") { EmulatorAdmin.deleteBucketFiles(bucket) }
    if (::circleId.isInitialized) runQuietly("the circle") { EmulatorAdmin.deleteCircle(circleId) }
    runQuietly("the profiles") { EmulatorAdmin.deleteProfiles(uids) }
    runQuietly("the accounts") { EmulatorAdmin.deleteAccounts(uids) }
  }

  /**
   * Runs one cleanup step, logging instead of throwing if it fails, so a cleanup failure can't hide
   * the test's own result.
   *
   * @param what what the step deletes, for the log
   * @param step the cleanup step
   */
  fun runQuietly(what: String, step: () -> Unit) {
    try {
      step()
    } catch (e: Exception) {
      Log.w(TAG, "Couldn't delete $what of run $runId", e)
    }
  }

  /**
   * Signs a user in straight through Firebase Auth, creating them on first sign-in. The token is
   * the one [FakeGoogleAccount] gives the app, so signing in again from the app gives the same
   * user.
   *
   * @param email the user's email address
   * @param name the user's display name
   * @return the user's uid
   */
  private suspend fun signIn(email: String, name: String): String {
    val credential = GoogleAuthProvider.getCredential(unsignedGoogleIdToken(email, name), null)
    val signedIn = checkNotNull(Firebase.auth.signInWithCredential(credential).await().user).uid
    uids += signedIn
    return signedIn
  }

  /**
   * Saves a place the patient often goes to in the circle. The app has no repository call for it
   * yet, so it is written the way the rules expect a caregiver to: one entry of `places`, by its
   * id, as `FirebaseCareCircleRepository.addPerson` writes one person.
   *
   * @return the saved place
   */
  private suspend fun saveFrequentPlace(): Place {
    val place =
        Place(
            id = circleRepository.newId(),
            name = PLACE_NAME,
            location = GeoLocation(lat = PLACE_LAT, lng = PLACE_LNG),
        )
    Firebase.firestore
        .collection("careCircles")
        .document(circleId)
        .update(FieldPath.of("places", place.id), place)
        .await()
    return place
  }

  /** Holds the names of what [setUp] creates. */
  companion object {
    private const val TAG = "E2eCaregiver"
    /** The caregiver's first name. */
    const val NAME = "Ada"
    /** Tom's first name. */
    const val PERSON_NAME = "Tom"
    /** The saved place's name. */
    const val PLACE_NAME = "Grandma's house"
    private const val PLACE_LAT = 46.5191
    private const val PLACE_LNG = 6.5668
  }
}
