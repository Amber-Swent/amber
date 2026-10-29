// this code was written with the aid of AI
package com.github.se.amber.ui.patient.seeMedias.pictures

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.patient.PatientUtilsTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
  private fun setContent(
      onBackClick: () -> Unit = {},
      onTabSelected: (Tab) -> Unit = {},
  ): SeePicturesViewModel {
    val viewModel = SeePicturesViewModel(mediaRepository, circleRepository, profileRepository)
    composeTestRule.setContent {
      AmberAppTheme(dynamicColor = false) {
        SeePicturesScreen(
            pictureViewModel = viewModel,
            onBackClick = onBackClick,
            onTabSelected = onTabSelected,
        )
      }
    }
    composeTestRule.waitForIdle()
    return viewModel
  }

  // ---------------------------------------------------------------------------------------------
  // Static parts
  // ---------------------------------------------------------------------------------------------

  @Test
  fun title_and_navigation_menus_are_displayed() {
    setContent()

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SeePicturesTestTags.TITLE_SEE_PICTURES).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU)
        .assertIsDisplayed()
  }

  @Test
  fun back_button_calls_onBackClick() {
    var clicked = false
    setContent(onBackClick = { clicked = true })

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    assertTrue(clicked)
  }

  @Test
  fun clicking_a_bottom_tab_calls_onTabSelected() {
    var selected: Tab? = null
    setContent(onTabSelected = { selected = it })

    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.tabTag(PatientTab.Home))
        .performClick()

    assertEquals(PatientTab.Home, selected)
  }

  // ---------------------------------------------------------------------------------------------
  // Loading
  // ---------------------------------------------------------------------------------------------

  @Test
  fun neither_error_nor_empty_message_is_displayed_while_loading() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flow { awaitCancellation() }

    val viewModel = setContent()

    assertTrue(viewModel.uiState.value is SeePicturesUIState.Loading)
    composeTestRule.onNodeWithTag(SeePicturesTestTags.ERROR_SEE_PICTURES).assertDoesNotExist()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SeePicturesTestTags.NO_PICTURES_SEE_PICTURES).assertDoesNotExist()
  }

  // ---------------------------------------------------------------------------------------------
  // Empty / Content
  // ---------------------------------------------------------------------------------------------

  @Test
  fun empty_message_is_displayed_when_there_are_no_pictures() {
    setContent()

    composeTestRule.onNodeWithTag(SeePicturesTestTags.NO_PICTURES_SEE_PICTURES).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SeePicturesTestTags.ERROR_SEE_PICTURES).assertDoesNotExist()
  }

  @Test
  fun pictures_are_displayed_when_there_are_pictures() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flowOf(
            listOf(
                MediaItem.Picture(id = "1", width = 400, height = 300),
                MediaItem.Picture(id = "2", width = 300, height = 400),
            )
        )

    val viewModel = setContent()

    assertTrue(viewModel.uiState.value is SeePicturesUIState.Content)
    composeTestRule.onNodeWithTag(SeePicturesTestTags.picture("1")).assertExists()
    composeTestRule.onNodeWithTag(SeePicturesTestTags.picture("2")).assertExists()
    composeTestRule.onNodeWithTag(SeePicturesTestTags.NO_PICTURES_SEE_PICTURES).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SeePicturesTestTags.ERROR_SEE_PICTURES).assertDoesNotExist()
  }

  // ---------------------------------------------------------------------------------------------
  // Blocking errors (displayed in the screen with a Retry button)
  // ---------------------------------------------------------------------------------------------

  @Test
  fun blocking_error_displays_message_and_retry_reloads_the_screen() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.failure(Exception("boom"))
    setContent()

    composeTestRule.onNodeWithTag(SeePicturesTestTags.ERROR_SEE_PICTURES).assertIsDisplayed()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SeePicturesTestTags.NO_PICTURES_SEE_PICTURES).assertDoesNotExist()

    coEvery { profileRepository.getCurrentProfile() } returns Result.success(profile)
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(SeePicturesTestTags.ERROR_SEE_PICTURES).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SeePicturesTestTags.NO_PICTURES_SEE_PICTURES).assertIsDisplayed()
  }

  // ---------------------------------------------------------------------------------------------
  // Non-blocking errors (Toast)
  // ---------------------------------------------------------------------------------------------

  @Test
  fun media_error_after_an_empty_list_is_shown_in_a_toast_then_cleared() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flow {
          emit(emptyList())
          throw RuntimeException("media error")
        }

    val viewModel = setContent()

    assertEquals("Failed to load media: media error", ShadowToast.getTextOfLatestToast())
    assertNull((viewModel.uiState.value as SeePicturesUIState.Empty).errorMsg)
    // The screen is still usable: no blocking error replaced it
    composeTestRule.onNodeWithTag(SeePicturesTestTags.NO_PICTURES_SEE_PICTURES).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SeePicturesTestTags.ERROR_SEE_PICTURES).assertDoesNotExist()
  }

  @Test
  fun media_error_after_pictures_is_shown_in_a_toast_and_keeps_the_pictures() {
    val picture = MediaItem.Picture(id = "1", width = 400, height = 300)
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flow {
          emit(listOf(picture))
          throw RuntimeException("media error")
        }

    val viewModel = setContent()

    assertEquals("Failed to load media: media error", ShadowToast.getTextOfLatestToast())
    val state = viewModel.uiState.value as SeePicturesUIState.Content
    assertNull(state.errorMsg)
    assertEquals(listOf(picture), state.allPictures)
    composeTestRule.onNodeWithTag(SeePicturesTestTags.ERROR_SEE_PICTURES).assertDoesNotExist()
  }
}
