// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.e2e

import android.os.Build
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
import com.github.se.amber.data.media.MediaFileStorageFirebase
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepositoryProvider
import com.github.se.amber.ui.caregiver.CaregiverHomeTestTags
import com.github.se.amber.ui.caregiver.upload.UploadTestTags
import com.github.se.amber.ui.caregiver.upload.media.AddPersonDialogTestTags
import com.github.se.amber.ui.caregiver.upload.media.DisplayWhoIsInvolvedTestTags
import com.github.se.amber.ui.caregiver.upload.media.UploadMediaLocationTestTags
import com.github.se.amber.ui.caregiver.upload.pictures.UploadPictureScreenTestTags
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.CaregiverTab
import com.github.se.amber.ui.theme.AmberAppTheme
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end journey of a caregiver uploading a picture from the phone's gallery.
 *
 * The app runs as on a phone, against the Firebase emulators (see [FirebaseEmulator]), with the
 * real system Photo Picker (see [GalleryPicture]). [E2eCaregiver] sets up the signed-in caregiver
 * and her circle, so the app opens on her home.
 *
 * Tapping Upload puts the media in the outbox, and a WorkManager job then sends it to Storage and
 * Firestore. The test waits for it through the app's media repository. It then checks the stored
 * details, and that the stored file is the gallery picture, byte for byte.
 *
 * Not covered yet: the patient's side, the typed place search (it goes over the network), and the
 * sign-in, which has tests of its own.
 *
 * Skipped below Android 13, where the Photo Picker isn't guaranteed. Also skipped when the
 * emulators are not running, unless `requireFirebaseEmulators=true` (see
 * [FirebaseEmulator.requireOrSkip]).
 */
@RunWith(AndroidJUnit4::class)
class UploadPictureEndToEndTest {

  @get:Rule val composeTestRule = createComposeRule()
  /** Never given an account: it stops the app from opening the real account picker. */
  private val googleAccount = FakeGoogleAccount(ApplicationProvider.getApplicationContext())
  private val steps = E2eSteps(composeTestRule)
  private val runId = UUID.randomUUID().toString().take(8)
  private val caregiver = E2eCaregiver(runId)
  private val picture = GalleryPicture(ApplicationProvider.getApplicationContext(), "e2e-$runId")

  /** Sets up the caregiver, adds the picture to the gallery, and opens the app. */
  @Before
  fun setUp() {
    assumeTrue(
        "The system Photo Picker is only guaranteed from Android 13",
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
    )
    caregiver.setUp()
    picture.insert()
    composeTestRule.setContent { AmberAppTheme { AmberApp(credentialManager = googleAccount) } }
  }

  /** Deletes the gallery picture and everything [E2eCaregiver] created. */
  @After
  fun cleanUp() {
    caregiver.runQuietly("the gallery picture") { picture.delete() }
    caregiver.cleanUp()
  }

  /**
   * The caregiver picks a gallery picture and types a description. She tags herself and Tom, then
   * adds Lea through the "+" dialog, which tags her too. She also picks a saved place. The media is
   * stored with all of them.
   */
  @Test
  fun caregiverUploadsAPictureWithPeopleAndAPlace() {
    openUploadPictureAndPick()
    steps.typeInto(UploadPictureScreenTestTags.DESCRIPTION, DESCRIPTION)
    steps.click(DisplayWhoIsInvolvedTestTags.personInvolved(caregiver.uid))
    steps.click(DisplayWhoIsInvolvedTestTags.personInvolved(caregiver.tom.id))
    steps.click(DisplayWhoIsInvolvedTestTags.ADD_PERSON)
    steps.typeInto(AddPersonDialogTestTags.FIRST_NAME, NEW_PERSON_NAME)
    steps.click(AddPersonDialogTestTags.SAVE)
    // Lea is saved in the circle, then shown as a chip, already selected
    steps.waitForNode(hasText(NEW_PERSON_NAME) and isSelected(), "the selected chip of Lea")
    steps.click(UploadMediaLocationTestTags.SAVED_PLACE)

    composeTestRule
        .onNodeWithTag(UploadPictureScreenTestTags.DESCRIPTION)
        .performScrollTo()
        .assert(E2eSteps.hasEditableText(DESCRIPTION))
    assertSelected(DisplayWhoIsInvolvedTestTags.personInvolved(caregiver.uid), E2eCaregiver.NAME)
    assertSelected(
        DisplayWhoIsInvolvedTestTags.personInvolved(caregiver.tom.id),
        E2eCaregiver.PERSON_NAME,
    )
    assertSelected(UploadMediaLocationTestTags.SAVED_PLACE, E2eCaregiver.PLACE_NAME)

    val media = uploadAndAwaitPicture()

    val circle = runBlocking { caregiver.circleRepository.getCircle(caregiver.circleId) }
    val lea = circle.getOrThrow().people.values.single { it.firstName == NEW_PERSON_NAME }
    assertEquals(DESCRIPTION, media.description)
    assertEquals(setOf(caregiver.uid, caregiver.tom.id, lea.id), media.shownIds.toSet())
    assertEquals(
        caregiver.savedPlace.location.copy(placeName = E2eCaregiver.PLACE_NAME),
        media.location,
    )
  }

  /**
   * Goes from the caregiver's home to the Upload Picture screen, picks the gallery picture in the
   * Photo Picker, and waits until the screen shows it.
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
   * Checks that a chip (a person or a saved place) shows its name and is selected.
   *
   * @param tag the chip's test tag
   * @param name the name the chip shows
   */
  private fun assertSelected(tag: String, name: String) {
    composeTestRule.onNodeWithTag(tag).performScrollTo().assertIsSelected().assert(hasText(name))
  }

  /**
   * Uploads, waits for the caregiver's home, then waits for the background send to store the media,
   * reading it through the app's media repository. Checks it is a picture by the caregiver, dated
   * this year, whose file in the circle's bucket is the gallery picture, byte for byte.
   *
   * @return the stored picture
   */
  private fun uploadAndAwaitPicture(): MediaItem.Picture {
    steps.click(UploadPictureScreenTestTags.UPLOAD_BUTTON)
    steps.waitFor(CaregiverHomeTestTags.TITLE)

    val media = runBlocking {
      withTimeout(SEND_TIMEOUT_MILLIS.milliseconds) {
        // the approved media are those in Firestore: the send is over once one shows
        MediaRepositoryProvider.repository.observeApprovedMedia(caregiver.circleId).first {
          it.isNotEmpty()
        }
      }
    }
    val stored =
        checkNotNull(media.single() as? MediaItem.Picture) { "The media isn't a picture: $media" }
    assertEquals(caregiver.uid, stored.authorId)
    assertEquals("careCircles/${caregiver.circleId}/media/${stored.id}.jpg", stored.storagePath)
    assertEquals(LocalDate.now().year, stored.date?.year)

    val circle = runBlocking { caregiver.circleRepository.getCircle(caregiver.circleId) }
    val file = File.createTempFile("e2e-$runId", ".jpg")
    try {
      runBlocking {
        MediaFileStorageFirebase().download(circle.getOrThrow(), stored.storagePath, file)
      }
      assertArrayEquals("the gallery picture is in the bucket", picture.bytes, file.readBytes())
    } finally {
      file.delete()
    }
    return stored
  }

  /** Holds the journey's text and timeouts. */
  private companion object {
    const val NEW_PERSON_NAME = "Lea"
    const val DESCRIPTION = "Sunday lunch at Grandma's house with Tom and Lea"
    const val PICKER_TIMEOUT_MILLIS = 15_000L
    // the send is a WorkManager job: it uploads the file, then creates the document
    const val SEND_TIMEOUT_MILLIS = 60_000L
  }
}
