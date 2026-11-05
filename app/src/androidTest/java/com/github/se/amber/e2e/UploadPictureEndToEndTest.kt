// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.e2e

import android.os.Build
import android.util.Log
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.amber.AmberApp
import com.github.se.amber.data.circle.FirebaseCareCircleRepository
import com.github.se.amber.data.media.MediaFileStorageFirebase
import com.github.se.amber.data.onboarding.FirebaseOnboardingRepository
import com.github.se.amber.data.profile.FirebaseProfileRepository
import com.github.se.amber.e2e.FirebaseEmulator.Service
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.location.Place
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepositoryProvider
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.ui.caregiver.CaregiverHomeTestTags
import com.github.se.amber.ui.caregiver.upload.UploadTestTags
import com.github.se.amber.ui.caregiver.upload.media.AddPersonDialogTestTags
import com.github.se.amber.ui.caregiver.upload.media.DisplayWhoIsInvolvedTestTags
import com.github.se.amber.ui.caregiver.upload.media.UploadMediaLocationTestTags
import com.github.se.amber.ui.caregiver.upload.pictures.UploadPictureScreenTestTags
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.CaregiverTab
import com.github.se.amber.ui.theme.AmberAppTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.firestore
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end journeys of a caregiver uploading a picture from the phone's gallery.
 *
 * An end-to-end test plays a real user on the real app: nothing is mocked, and the implementations
 * run together as they do on a phone. Here that is the app's screens and ViewModels, the system
 * Photo Picker, the selected-picture reader, the media repository, and Firebase's security rules.
 * Firebase itself is replaced by its local emulators (Auth, Firestore, Storage; see
 * [FirebaseEmulator]), so no request leaves the device and the computer running the emulators.
 *
 * ## The journeys
 * Each one opens the Upload Picture screen, picks the gallery picture in the Photo Picker, types a
 * description, and then either:
 * - tags people ([caregiverUploadsAPictureShowingPeople]): the caregiver herself and Tom, a person
 *   of the circle without an account, then Lea, added through the "+" Add Person dialog, which tags
 *   her too;
 * - or picks one of the circle's saved places ([caregiverUploadsAPictureAtASavedPlace]).
 *
 * It then checks what the screen shows (picture selected, description, selected chips or place),
 * uploads, and checks what was stored.
 *
 * ## How an upload is stored, and what the test waits for
 * Tapping Upload doesn't send anything yet: the media repository saves the media in its outbox, a
 * folder on the phone, and schedules a background job (WorkManager) that sends it. That job uploads
 * the file to the circle's storage bucket, then creates the media's document in Firestore. The test
 * waits for that document through the app's [com.github.se.amber.model.media.MediaRepository]
 * (`observeApprovedMedia`, as a screen listing the circle's media would). It then downloads the
 * file through [MediaFileStorageFirebase] and checks it is the very picture picked in the gallery,
 * byte for byte.
 *
 * ## Set-up, without the sign-in
 * There are no sign-in steps on screen, since the sign-in isn't what this test is about. [signIn]
 * signs the caregiver in straight through Firebase Auth, the quickest way. Then
 * [setUpCaregiverAndStartApp] creates her profile and her care circle through the app's own
 * repositories, as onboarding does, so the app opens already signed in, on her home.
 *
 * Each care circle stores its files in a Firebase Storage bucket of its own, which the backend
 * creates and records in the circle (`storageBucket`) once the circle exists. The emulators can't
 * create buckets, so the backend function skips itself there, and [EmulatorAdmin] records a bucket
 * instead. That is the only thing the test does in place of the backend. [cleanUp] then deletes
 * everything the test created, and only that.
 *
 * ## Not covered (yet)
 * - The patient's side: this test doesn't check how the patient then sees the picture (the media
 *   card on the patient's See Pictures screen). That might be added later, with a patient joining
 *   the circle through an invitation.
 * - The typed place search: it uses Android's Geocoder, which goes over the network, so the place
 *   comes from the circle's saved places instead.
 * - The sign-in, which has tests of its own.
 *
 * Skipped when the emulators are not running, unless the `requireFirebaseEmulators=true`
 * instrumentation argument makes that a failure (see [FirebaseEmulator.requireOrSkip]), and below
 * Android 13, where the system Photo Picker isn't guaranteed (see [GalleryPicture]).
 */
@RunWith(AndroidJUnit4::class)
class UploadPictureEndToEndTest {

  @get:Rule val composeTestRule = createComposeRule()
  /**
   * The app's Google account picker. The test signs in without it, so it is never given an account:
   * it is there so that the app can't open the real account picker if a journey ever reaches the
   * sign-in screen.
   */
  private val googleAccount = FakeGoogleAccount(ApplicationProvider.getApplicationContext())
  private val steps = E2eSteps(composeTestRule)
  private val runId = UUID.randomUUID().toString().take(8)

  private val bucket = "demo-amber-e2e-$runId"
  private val picture = GalleryPicture(ApplicationProvider.getApplicationContext(), "e2e-$runId")
  /** The uids of the accounts the test created, for [cleanUp] to delete them. */
  private val uids = mutableListOf<String>()
  private var circleId: String? = null
  private lateinit var caregiverUid: String
  private lateinit var tom: Person
  private lateinit var savedPlace: Place
  /**
   * The app's circle repository, for the setup and the checks a screen would make. Built on first
   * use, once [FirebaseEmulator.useForDefaultApp] has pointed Firestore at the emulator.
   */
  private val circleRepository by lazy { FirebaseCareCircleRepository() }

  /**
   * Checks the emulators, sets up the caregiver and her circle as onboarding would, adds the
   * picture to the gallery, and opens the app as the signed-in caregiver.
   */
  @Before
  fun setUpCaregiverAndStartApp() {
    assumeTrue(
        "The system Photo Picker is only guaranteed from Android 13",
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
    )
    FirebaseEmulator.requireOrSkip(setOf(Service.AUTH, Service.FIRESTORE, Service.STORAGE))
    FirebaseEmulator.useForDefaultApp()
    FirebaseEmulator.useStorageFor(bucket)

    runBlocking {
      caregiverUid = signIn("ada-$runId@amber.test", CAREGIVER_NAME)
      val circle = createCaregiverAndCircle()
      circleId = circle.id
      tom = circleRepository.addPerson(circle.id, Person(firstName = PERSON_NAME)).getOrThrow()
      savedPlace = saveFrequentPlace(circle.id)
    }
    EmulatorAdmin.setStorageBucket(checkNotNull(circleId), bucket)
    picture.insert()

    composeTestRule.setContent { AmberAppTheme { AmberApp(credentialManager = googleAccount) } }
  }

  /**
   * Deletes what the test created: the gallery picture, the bucket's files, the circle and its
   * media, the profile and the account. A failure is logged, not thrown, so it can't hide the
   * test's own result, and each step runs even if an earlier one failed.
   */
  @After
  fun cleanUp() {
    runQuietly("the gallery picture") { picture.delete() }
    if (uids.isEmpty()) return // skipped before anything was created
    runQuietly("the bucket's files") { EmulatorAdmin.deleteBucketFiles(bucket) }
    circleId?.let { id -> runQuietly("the circle") { EmulatorAdmin.deleteCircle(id) } }
    runQuietly("the profiles") { EmulatorAdmin.deleteProfiles(uids) }
    runQuietly("the accounts") { EmulatorAdmin.deleteAccounts(uids) }
  }

  /**
   * The caregiver tags herself and Tom, a person of the circle without an account, then adds Lea
   * through the "+" dialog, which tags her too. The media is stored with the three of them.
   */
  @Test
  fun caregiverUploadsAPictureShowingPeople() {
    openUploadPictureAndPick()
    steps.typeInto(UploadPictureScreenTestTags.DESCRIPTION, PEOPLE_DESCRIPTION)

    steps.click(DisplayWhoIsInvolvedTestTags.personInvolved(caregiverUid))
    steps.click(DisplayWhoIsInvolvedTestTags.personInvolved(tom.id))
    steps.click(DisplayWhoIsInvolvedTestTags.ADD_PERSON)
    steps.typeInto(AddPersonDialogTestTags.FIRST_NAME, NEW_PERSON_NAME)
    steps.click(AddPersonDialogTestTags.SAVE)
    // Lea is saved in the circle, then shown as a chip, already selected
    steps.waitForNode(hasText(NEW_PERSON_NAME) and isSelected(), "the selected chip of Lea")

    assertDescriptionShown(PEOPLE_DESCRIPTION)
    assertChipSelected(caregiverUid, CAREGIVER_NAME)
    assertChipSelected(tom.id, PERSON_NAME)

    val media = uploadAndAwaitMedia()

    assertEquals(PEOPLE_DESCRIPTION, media.description)
    assertEquals("the caregiver, Tom and Lea are shown: ${media.shownIds}", 3, media.shownIds.size)
    assertTrue(media.shownIds.containsAll(listOf(caregiverUid, tom.id)))
    val circle = runBlocking { circleRepository.getCircle(checkNotNull(circleId)).getOrThrow() }
    val lea = circle.people.values.single { it.firstName == NEW_PERSON_NAME }
    assertTrue("Lea is shown", lea.id in media.shownIds)
    assertNull(media.location)
  }

  /** The caregiver picks one of the circle's saved places; the media is stored with that place. */
  @Test
  fun caregiverUploadsAPictureAtASavedPlace() {
    openUploadPictureAndPick()
    steps.typeInto(UploadPictureScreenTestTags.DESCRIPTION, PLACE_DESCRIPTION)
    steps.click(UploadMediaLocationTestTags.SAVED_PLACE)

    assertDescriptionShown(PLACE_DESCRIPTION)
    composeTestRule
        .onNodeWithTag(UploadMediaLocationTestTags.SAVED_PLACE)
        .performScrollTo()
        .assertIsSelected()
        .assert(hasText(PLACE_NAME))

    val media = uploadAndAwaitMedia()

    assertEquals(PLACE_DESCRIPTION, media.description)
    assertEquals(emptyList<String>(), media.shownIds)
    assertEquals(savedPlace.location.copy(placeName = PLACE_NAME), media.location)
  }

  /**
   * Creates the signed-in caregiver's profile and her care circle, as the onboarding screens do.
   *
   * @return the new circle
   */
  private suspend fun createCaregiverAndCircle(): CareCircle {
    FirebaseProfileRepository()
        .createProfile(Person(firstName = CAREGIVER_NAME, lastName = "Lovelace"), Role.CAREGIVER)
        .getOrThrow()
    return FirebaseOnboardingRepository().createInitialCareCircle("E2E family").getOrThrow()
  }

  /**
   * Saves a place the patient often goes to in the circle, as the signed-in caregiver. The app has
   * no repository call for it yet, so it is written the way the rules expect a caregiver to: one
   * entry of `places`, by its id, as `FirebaseCareCircleRepository.addPerson` writes one person.
   *
   * @param circleId the circle to add the place to
   * @return the saved place
   */
  private suspend fun saveFrequentPlace(circleId: String): Place {
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

  /**
   * Goes from the caregiver's home to the Upload Picture screen, picks the gallery picture in the
   * Photo Picker, and waits until the screen has read it.
   */
  private fun openUploadPictureAndPick() {
    steps.waitFor(CaregiverHomeTestTags.TITLE)
    steps.click(BottomNavigationMenuTestTags.tabTag(CaregiverTab.Upload))
    steps.click(UploadTestTags.PICTURE_BUTTON)
    steps.waitFor(UploadPictureScreenTestTags.TITLE)
    steps.click(UploadPictureScreenTestTags.SELECT_PICTURE)
    picture.pickInPhotoPicker(PICKER_TIMEOUT_MILLIS)
    steps.waitFor(UploadPictureScreenTestTags.PICTURE_SELECTED)
    composeTestRule
        .onNodeWithTag(UploadPictureScreenTestTags.PICTURE_SELECTED)
        .performScrollTo()
        .assertIsDisplayed()
  }

  /**
   * Checks that the description field shows what was typed.
   *
   * @param description the typed description
   */
  private fun assertDescriptionShown(description: String) {
    composeTestRule
        .onNodeWithTag(UploadPictureScreenTestTags.DESCRIPTION)
        .performScrollTo()
        .assert(E2eSteps.hasEditableText(description))
  }

  /**
   * Checks that the "who is involved" chip of a person shows their name and is selected.
   *
   * @param id the person's id: a member's uid, or the id of a person without an account
   * @param name the name the chip shows
   */
  private fun assertChipSelected(id: String, name: String) {
    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.personInvolved(id))
        .performScrollTo()
        .assertIsSelected()
        .assert(hasText(name))
  }

  /**
   * Uploads, waits for the screen to go back to the caregiver's home, then reads the media back
   * through the app's media repository, once the background send has stored it, and downloads its
   * file from the circle's bucket. Checks the parts every upload shares: a picture by the
   * caregiver, dated this year, whose file in Storage is the gallery picture, byte for byte.
   *
   * @return the stored picture
   */
  private fun uploadAndAwaitMedia(): MediaItem.Picture {
    steps.click(UploadPictureScreenTestTags.UPLOAD_BUTTON)
    steps.waitFor(CaregiverHomeTestTags.TITLE)

    val id = checkNotNull(circleId)
    val media = runBlocking {
      withTimeout(SEND_TIMEOUT_MILLIS.milliseconds) {
        // the approved media are those in Firestore: the send is over once one shows
        MediaRepositoryProvider.repository.observeApprovedMedia(id).first { it.isNotEmpty() }
      }
    }
    val stored = media.single() as MediaItem.Picture
    assertEquals(caregiverUid, stored.authorId)
    assertEquals("careCircles/$id/media/${stored.id}.jpg", stored.storagePath)
    assertEquals(LocalDate.now().year, stored.date?.year)

    val circle = runBlocking { circleRepository.getCircle(id).getOrThrow() }
    val file = File.createTempFile("e2e-$runId", ".jpg")
    try {
      runBlocking { MediaFileStorageFirebase().download(circle, stored.storagePath, file) }
      assertArrayEquals(
          "the gallery picture is in the circle's bucket",
          picture.bytes,
          file.readBytes(),
      )
    } finally {
      file.delete()
    }
    return stored
  }

  /**
   * Signs a user in straight through Firebase Auth on the Auth emulator, creating them on first
   * sign-in. The app's sign-in code is left out on purpose: this test is about the upload, and this
   * is the quickest way. The token is the one [FakeGoogleAccount] gives the app, so signing in
   * again from the app gives the same user.
   *
   * @param email the user's email address
   * @param name the user's display name
   * @return the user's uid
   */
  private suspend fun signIn(email: String, name: String): String {
    val credential = GoogleAuthProvider.getCredential(unsignedGoogleIdToken(email, name), null)
    val uid = checkNotNull(Firebase.auth.signInWithCredential(credential).await().user).uid
    uids += uid
    return uid
  }

  /**
   * Runs one cleanup step, logging instead of throwing if it fails.
   *
   * @param what what the step deletes, for the log
   * @param step the cleanup step
   */
  private fun runQuietly(what: String, step: () -> Unit) {
    try {
      step()
    } catch (e: Exception) {
      Log.w(TAG, "Couldn't delete $what of run $runId", e)
    }
  }

  /** Holds the names the journeys use and their timeouts. */
  private companion object {
    const val TAG = "UploadPictureE2E"
    const val CAREGIVER_NAME = "Ada"
    const val PERSON_NAME = "Tom"
    const val NEW_PERSON_NAME = "Lea"
    const val PLACE_NAME = "Grandma's house"
    const val PLACE_LAT = 46.5191
    const val PLACE_LNG = 6.5668
    const val PEOPLE_DESCRIPTION = "Sunday lunch with Tom and Lea"
    const val PLACE_DESCRIPTION = "Tea at Grandma's house"
    const val PICKER_TIMEOUT_MILLIS = 15_000L
    // the send is a WorkManager job: it uploads the file, then creates the document
    const val SEND_TIMEOUT_MILLIS = 60_000L
  }
}
