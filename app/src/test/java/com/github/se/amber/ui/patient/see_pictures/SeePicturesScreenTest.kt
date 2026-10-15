package com.github.se.amber.ui.patient.see_pictures

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class SeePicturesScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val mediaRepository = mockk<MediaRepository>()
  private val circleRepository = mockk<CareCircleRepository>()
  private val profileRepository = mockk<ProfileRepository>()

  private val profile =
      UserProfile(
          uid = "patient",
          role = Role.PATIENT,
          person = Person(),
          circleIds = listOf("circle1"),
      )

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(profile)
    coEvery { circleRepository.getCircle("circle1") } returns
        Result.success(CareCircle(id = "circle1"))
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(emptyList())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  // The ViewModel is created before the composition: its init runs right away (Unconfined)
  private fun setContent(onBackClick: () -> Unit = {}): SeePicturesViewModel {
    val viewModel = SeePicturesViewModel(mediaRepository, circleRepository, profileRepository)
    composeTestRule.setContent {
      AmberAppTheme(dynamicColor = false) {
        SeePicturesScreen(
            pictureViewModel = viewModel,
            onBackClick = onBackClick,
            onTabSelected = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    return viewModel
  }

  @Test
  fun title_and_navigation_menus_are_displayed() {
    setContent()

    composeTestRule.onNodeWithTag(SeePicturesScreenTestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithText("See Pictures").assertIsDisplayed()
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU)
        .assertIsDisplayed()
  }

  @Test
  fun empty_message_is_displayed_when_there_are_no_pictures() {
    setContent()

    composeTestRule.onNodeWithTag(SeePicturesScreenTestTags.NO_PICTURES).assertIsDisplayed()
    composeTestRule.onNodeWithText("There are no pictures yet.").assertIsDisplayed()
  }

  @Test
  fun error_and_retry_are_displayed_when_the_profile_has_no_circle() {
    coEvery { profileRepository.getCurrentProfile() } returns
        Result.success(profile.copy(circleIds = emptyList()))

    setContent()

    composeTestRule.onNodeWithTag(SeePicturesScreenTestTags.ERROR).assertIsDisplayed()
    composeTestRule.onNodeWithText("No care circle found for this patient.").assertIsDisplayed()
    composeTestRule.onNodeWithTag(SeePicturesScreenTestTags.RETRY).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SeePicturesScreenTestTags.NO_PICTURES).assertDoesNotExist()
  }

  @Test
  fun empty_message_is_not_displayed_when_there_are_pictures() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flowOf(listOf(MediaItem.Picture(id = "1", width = 400, height = 300)))

    setContent()

    composeTestRule.onNodeWithTag(SeePicturesScreenTestTags.NO_PICTURES).assertDoesNotExist()
  }

  @Test
  fun back_button_calls_onBackClick() {
    var clicked = false
    setContent(onBackClick = { clicked = true })

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    assertTrue(clicked)
  }

  @Test
  fun media_error_is_shown_in_a_toast_then_cleared() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flow { throw RuntimeException("media error") }

    val viewModel = setContent()

    assertEquals("Failed to load media: media error", ShadowToast.getTextOfLatestToast())
    assertNull(viewModel.uiState.value.errorMsg)
  }

  @Test
  fun error_is_displayed_in_the_screen_when_the_profile_fails_to_load() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.failure(Exception("boom"))

    setContent()

    composeTestRule.onNodeWithTag(SeePicturesScreenTestTags.ERROR).assertIsDisplayed()
    composeTestRule.onNodeWithText("Failed to load the profile: boom").assertIsDisplayed()
  }

  @Test
  fun retry_button_reloads_the_screen() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.failure(Exception("boom"))
    setContent()
    composeTestRule.onNodeWithTag(SeePicturesScreenTestTags.ERROR).assertIsDisplayed()

    coEvery { profileRepository.getCurrentProfile() } returns Result.success(profile)
    composeTestRule.onNodeWithTag(SeePicturesScreenTestTags.RETRY).performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(SeePicturesScreenTestTags.ERROR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SeePicturesScreenTestTags.NO_PICTURES).assertIsDisplayed()
  }
}
