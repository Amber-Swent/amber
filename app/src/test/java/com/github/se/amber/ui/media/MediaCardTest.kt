// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.media

import android.content.Context
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.theme.AmberAppTheme
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Month
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MediaCardTest {

  @get:Rule val composeTestRule = createComposeRule()

  /** Text of [id] in the current test language, with [args] for its placeholders. */
  private fun string(id: Int, vararg args: Any): String =
      ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

  private val profileRepository = mockk<ProfileRepository>()
  private val circleRepository = mockk<CareCircleRepository>()

  private val authorProfile = userProfile("author", "Alice")

  private fun userProfile(uid: String, firstName: String, circleIds: List<String> = emptyList()) =
      UserProfile(
          uid = uid,
          person = Person(id = uid, firstName = firstName),
          circleIds = circleIds,
      )

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    ShadowToast.reset()
    coEvery { profileRepository.getProfile("author") } returns Result.success(authorProfile)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  // The ViewModel is created before the composition. Its load only runs once the card is expanded.
  private fun setCard(
      media: MediaItem,
      testTag: String = "media_card",
      modifier: Modifier = Modifier,
      fontScale: Float = 1f,
  ) {
    val viewModel = MediaCardViewModel(profileRepository, circleRepository)
    composeTestRule.setContent {
      val density = LocalDensity.current
      AmberAppTheme(dynamicColor = false) {
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
          MediaCard(
              modifier = modifier,
              media = media,
              testTag = testTag,
              mediaCardViewModel = viewModel,
          )
        }
      }
    }
    composeTestRule.waitForIdle()
  }

  private fun picture(
      id: String = "m1",
      description: String = "A nice day",
      date: MemoryDate? = null,
      location: GeoLocation? = null,
      shownIds: List<String> = emptyList(),
  ) =
      MediaItem.Picture(
          id = id,
          authorId = "author",
          description = description,
          date = date,
          location = location,
          shownIds = shownIds,
      )

  /** Clicks the toggle button. Set [expanded] to true if the card is currently expanded. */
  private fun toggle(id: String = "m1", expanded: Boolean = false) {
    val tag =
        if (expanded) MediaCardTestTags.toggleButtonTextHide(id)
        else MediaCardTestTags.toggleButtonTextShow(id)
    composeTestRule.onNodeWithTag(tag).performClick()
    composeTestRule.waitForIdle()
  }

  // ---------- toggle ----------

  @Test
  fun toggleButton_initiallyShowsShowInfos() {
    setCard(picture())
    composeTestRule.onNodeWithTag(MediaCardTestTags.toggleButtonTextShow("m1")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MediaCardTestTags.toggleButtonTextHide("m1")).assertDoesNotExist()
  }

  @Test
  fun collapsed_hidesDetails() {
    setCard(picture())

    composeTestRule.onNodeWithTag(MediaCardTestTags.description("m1")).assertDoesNotExist()
    composeTestRule.onNodeWithTag(MediaCardTestTags.addedBy("m1")).assertDoesNotExist()
    composeTestRule.onNodeWithTag(MediaCardTestTags.loading("m1")).assertDoesNotExist()
  }

  @Test
  fun toggleButton_expandsThenCollapses() {
    setCard(picture())

    toggle()
    composeTestRule.onNodeWithTag(MediaCardTestTags.toggleButtonTextHide("m1")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MediaCardTestTags.toggleButtonTextShow("m1")).assertDoesNotExist()
    composeTestRule.onNodeWithTag(MediaCardTestTags.description("m1")).assertIsDisplayed()

    toggle(expanded = true)
    composeTestRule.onNodeWithTag(MediaCardTestTags.toggleButtonTextShow("m1")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MediaCardTestTags.toggleButtonTextHide("m1")).assertDoesNotExist()
    composeTestRule.onNodeWithTag(MediaCardTestTags.description("m1")).assertDoesNotExist()
  }

  // ---------- layout ----------

  private val longLocation =
      GeoLocation(
          lat = 46.5,
          lng = 6.6,
          placeName = "Chemin des Très Longues Prairies de la Vallée de la Broye, Lausanne, Suisse",
      )

  private val cardWidth = 200.dp

  /** The toggle, the date and the location are displayed, and the toggle overlaps neither. */
  private fun assertHeaderLayoutIsSound(toggleTag: String) {
    composeTestRule.onNodeWithTag(toggleTag).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MediaCardTestTags.date("m1")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MediaCardTestTags.location("m1")).assertIsDisplayed()

    val toggleBounds = composeTestRule.onNodeWithTag(toggleTag).getUnclippedBoundsInRoot()
    val dateBounds =
        composeTestRule.onNodeWithTag(MediaCardTestTags.date("m1")).getUnclippedBoundsInRoot()
    val locationBounds =
        composeTestRule.onNodeWithTag(MediaCardTestTags.location("m1")).getUnclippedBoundsInRoot()
    assertTrue("toggle must stay inside the card", toggleBounds.right <= cardWidth)
    assertTrue("toggle must not overlap the date", toggleBounds.right <= dateBounds.left)
    assertTrue("toggle must not overlap the location", toggleBounds.right <= locationBounds.left)
  }

  /** Checks the header layout both collapsed and expanded, in a narrow card with long texts. */
  private fun assertToggleSurvivesConstrainedLayout(fontScale: Float) {
    setCard(
        picture(
            date = MemoryDate(year = 2024, month = Month.MARCH, day = 5),
            location = longLocation,
        ),
        modifier = Modifier.width(cardWidth),
        fontScale = fontScale,
    )

    assertHeaderLayoutIsSound(MediaCardTestTags.toggleButtonTextShow("m1"))

    // still clickable: the card expands, and the header (with the longer "hide" text) stays sound
    toggle()
    assertHeaderLayoutIsSound(MediaCardTestTags.toggleButtonTextHide("m1"))
    composeTestRule.onNodeWithTag(MediaCardTestTags.description("m1")).assertIsDisplayed()
  }

  @Test
  fun toggleButton_staysVisibleAndClickable_withNarrowWidthAndLongLocation() {
    assertToggleSurvivesConstrainedLayout(fontScale = 1f)
  }

  @Test
  fun toggleButton_staysVisibleAndClickable_withNarrowWidthAndLargeFont() {
    assertToggleSurvivesConstrainedLayout(fontScale = 2f)
  }

  @Test
  @Config(qualifiers = "fr")
  fun toggleButton_staysVisibleAndClickable_inFrench_withNarrowWidthAndLargeFont() {
    assertToggleSurvivesConstrainedLayout(fontScale = 2f)
  }

  // ---------- date ----------

  @Test
  fun date_full_isDayMonthYear() {
    setCard(picture(date = MemoryDate(year = 2024, month = Month.MARCH, day = 5)))
    composeTestRule.onNodeWithTag(MediaCardTestTags.date("m1")).assertTextEquals("05/03/2024")
  }

  @Test
  fun date_monthAndYear_isMonthYear() {
    setCard(picture(date = MemoryDate(year = 2024, month = Month.MARCH, day = null)))
    composeTestRule.onNodeWithTag(MediaCardTestTags.date("m1")).assertTextEquals("03/2024")
  }

  @Test
  fun date_yearOnly_isYear() {
    setCard(picture(date = MemoryDate(year = 2024, month = null, day = null)))
    composeTestRule.onNodeWithTag(MediaCardTestTags.date("m1")).assertTextEquals("2024")
  }

  @Test
  fun date_null_isUnknownDate() {
    setCard(picture(date = null))
    composeTestRule
        .onNodeWithTag(MediaCardTestTags.date("m1"))
        .assertTextEquals(string(R.string.mediaCardUnknownDate))
  }

  // ---------- loading ----------

  @Test
  fun expanded_whileLoading_showsOnlyProgressIndicator() {
    val gate = CompletableDeferred<Unit>()
    coEvery { profileRepository.getProfile("author") } coAnswers
        {
          gate.await()
          Result.success(authorProfile)
        }
    setCard(picture())

    toggle()

    composeTestRule.onNodeWithTag(MediaCardTestTags.loading("m1")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MediaCardTestTags.addedBy("m1")).assertDoesNotExist()
    composeTestRule.onNodeWithTag(MediaCardTestTags.relatedList("m1")).assertDoesNotExist()
  }

  @Test
  fun expanded_loadingFinishes_showsContent() {
    val gate = CompletableDeferred<Unit>()
    coEvery { profileRepository.getProfile("author") } coAnswers
        {
          gate.await()
          Result.success(authorProfile)
        }
    setCard(picture())
    toggle()

    gate.complete(Unit)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(MediaCardTestTags.loading("m1")).assertDoesNotExist()
    composeTestRule.onNodeWithTag(MediaCardTestTags.description("m1")).assertIsDisplayed()
  }

  // ---------- description / author ----------

  @Test
  fun expanded_showsDescriptionAndAuthor() {
    setCard(picture(description = "A nice day"))

    toggle()

    composeTestRule
        .onNodeWithTag(MediaCardTestTags.description("m1"))
        .assertTextEquals("A nice day")
    composeTestRule
        .onNodeWithTag(MediaCardTestTags.addedBy("m1"))
        .assertTextEquals(string(R.string.mediaCardAddedBy, "Alice"))
  }

  // ---------- related persons ----------

  @Test
  fun expanded_withRelatedPersons_showsThem() {
    coEvery { profileRepository.getCurrentProfile() } returns
        Result.success(userProfile("me", "Patient", listOf("circle1")))
    coEvery { circleRepository.getCircle("circle1") } returns
        Result.success(
            CareCircle(
                id = "circle1",
                people =
                    mapOf(
                        "p1" to Person(id = "p1", firstName = "Bob"),
                        "p2" to Person(id = "p2", firstName = "Carol"),
                    ),
            )
        )
    setCard(picture(shownIds = listOf("p1", "p2")))

    toggle()

    composeTestRule.onNodeWithTag(MediaCardTestTags.relatedTitle("m1")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MediaCardTestTags.relatedList("m1")).assertIsDisplayed()
    // every nickname except the last one is followed by a comma
    composeTestRule.onNodeWithTag(MediaCardTestTags.relatedItem("m1", 0)).assertTextEquals("Bob,")
    composeTestRule.onNodeWithTag(MediaCardTestTags.relatedItem("m1", 1)).assertTextEquals("Carol")
  }

  @Test
  fun expanded_withoutRelatedPersons_hidesList() {
    setCard(picture(shownIds = emptyList()))

    toggle()

    composeTestRule.onNodeWithTag(MediaCardTestTags.relatedTitle("m1")).assertDoesNotExist()
    composeTestRule.onNodeWithTag(MediaCardTestTags.relatedList("m1")).assertDoesNotExist()
  }

  // ---------- location ----------

  @Test
  fun expanded_locationWithPlaceName_showsPlaceName() {
    setCard(picture(location = GeoLocation(lat = 46.5, lng = 6.6, placeName = "Lausanne")))
    toggle()
    composeTestRule.onNodeWithTag(MediaCardTestTags.location("m1")).assertTextEquals("Lausanne")
  }

  @Test
  fun expanded_locationWithBlankName_showsCoordinates() {
    setCard(picture(location = GeoLocation(lat = 46.5191, lng = 6.5668, placeName = " ")))
    toggle()
    composeTestRule
        .onNodeWithTag(MediaCardTestTags.location("m1"))
        .assertTextEquals("46.5191, 6.5668")
  }

  @Test
  fun expanded_locationWithNullName_showsCoordinates() {
    setCard(picture(location = GeoLocation(lat = 46.5191, lng = 6.5668, placeName = null)))
    toggle()
    composeTestRule
        .onNodeWithTag(MediaCardTestTags.location("m1"))
        .assertTextEquals("46.5191, 6.5668")
  }

  @Test
  fun expanded_locationWithNothing_showsUnknownLocation() {
    setCard(picture(location = GeoLocation(lat = null, lng = null, placeName = null)))
    toggle()
    composeTestRule
        .onNodeWithTag(MediaCardTestTags.location("m1"))
        .assertTextEquals(string(R.string.mediaCardUnknownLocation))
  }

  @Test
  fun expanded_locationWithOnlyLat_showsUnknownLocation() {
    setCard(picture(location = GeoLocation(lat = 46.5, lng = null, placeName = null)))
    toggle()
    composeTestRule
        .onNodeWithTag(MediaCardTestTags.location("m1"))
        .assertTextEquals(string(R.string.mediaCardUnknownLocation))
  }

  @Test
  fun expanded_nullLocation_hidesLocationBox() {
    setCard(picture(location = null))
    toggle()
    composeTestRule.onNodeWithTag(MediaCardTestTags.location("m1")).assertDoesNotExist()
  }

  // ---------- errors ----------

  @Test
  fun authorNotFound_showsToast() {
    coEvery { profileRepository.getProfile("author") } returns Result.success(null)

    setCard(picture())
    toggle()

    assertEquals(string(R.string.mediaCardErrorAuthorNotFound), ShadowToast.getTextOfLatestToast())
  }

  @Test
  fun noError_showsNoToast() {
    setCard(picture())
    assertEquals(0, ShadowToast.shownToastCount())
  }

  @Test
  fun error_whenExpanded_noLoadingIndicator() {
    coEvery { profileRepository.getProfile("author") } returns Result.success(null)
    setCard(picture())

    toggle()

    composeTestRule.onNodeWithTag(MediaCardTestTags.loading("m1")).assertDoesNotExist()
  }

  @Test
  fun errorToast_isShownOnlyOnce() {
    coEvery { profileRepository.getProfile("author") } returns Result.success(null)

    setCard(picture())
    toggle()
    composeTestRule.waitForIdle()

    assertEquals(1, ShadowToast.shownToastCount())
  }

  @Test
  @Config(qualifiers = "fr")
  fun cardIsShownInFrench() {
    setCard(picture(location = GeoLocation(lat = null, lng = null, placeName = null)))

    composeTestRule.onNodeWithTag(MediaCardTestTags.date("m1")).assertTextEquals("Date inconnue")
    composeTestRule.onNodeWithTag(MediaCardTestTags.location("m1")).assertTextEquals("Lieu inconnu")
    composeTestRule
        .onNodeWithTag(MediaCardTestTags.toggleButtonTextShow("m1"))
        .assertTextEquals("voir les infos")
    toggle()
    composeTestRule
        .onNodeWithTag(MediaCardTestTags.toggleButtonTextHide("m1"))
        .assertTextEquals("masquer les infos")
    composeTestRule
        .onNodeWithTag(MediaCardTestTags.addedBy("m1"))
        .assertTextEquals("Ajouté par Alice")
  }
}
