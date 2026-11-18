// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.

package com.github.se.amber.ui.patient.seeMedias.medias

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.media.MediaCardViewModel
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.patient.PatientUtilsTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SeeGeneralMediasTest {
  @get:Rule val composeTestRule = createComposeRule()

  private object Tags {
    const val TITLE = "general_title"
    const val ERROR = "general_error"
    const val EMPTY = "general_empty"
    const val LOADING = "general_loading"

    fun card(id: String) = "general_card_$id"

    fun item(id: String) = "general_item_$id"
  }

  // Existing strings are reused: the shared screen does not care which ones it gets
  private val config =
      SeeGeneralMediasConfig(
          titleRes = R.string.seeAudiosTitle,
          retryRes = R.string.seeAudiosRetry,
          emptyRes = R.string.seeAudiosEmpty,
          titleTag = Tags.TITLE,
          errorTag = Tags.ERROR,
          emptyTag = Tags.EMPTY,
          loadingTag = Tags.LOADING,
          cardTag = { id -> Tags.card(id) },
      )

  private val profile = UserProfile(uid = "patient", role = Role.PATIENT, person = Person())
  private val circle = CareCircle(id = "circle1")
  private val first = MediaItem.Audio(id = "a1")
  private val second = MediaItem.Audio(id = "a2")

  private val circleRepository = mockk<CareCircleRepository>()
  private val profileRepository = mockk<ProfileRepository>()
  private val activeCircleRepository = mockk<ActiveCircleRepository>()

  private val uiState =
      mutableStateOf<SeeGeneralMediasUIState<MediaItem.Audio>>(SeeGeneralMediasUIState.Loading)
  private var receivedCircle: CareCircle? = null

  /** Text of [id] in the current test language, with [args] for its placeholders. */
  private fun string(id: Int, vararg args: Any): String =
      ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

  // MediaCard's ViewModel is provided without its Firebase defaults
  private val mediaCardOwner =
      object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
        override val viewModelStore = ViewModelStore()
        override val defaultViewModelProviderFactory =
            object : ViewModelProvider.Factory {
              @Suppress("UNCHECKED_CAST")
              override fun <T : ViewModel> create(modelClass: Class<T>): T =
                  MediaCardViewModel(profileRepository, circleRepository, activeCircleRepository)
                      as T
            }
      }

  /**
   * Displays [SeeGeneralMedias] with a test configuration and a simple text for each item, so that
   * the tests only check what the general screen itself shows.
   *
   * @param state the state the screen starts with; a test can change [uiState] afterwards to check
   *   that the screen follows it.
   * @param onRetry called when the Retry button of the blocking error is pressed.
   * @param onErrorShown called once the non-blocking error was shown in a Toast.
   * @param onBackClick called when the top navigation action is selected.
   * @param onTabSelected called when a bottom navigation tab is selected.
   * @param titleRes string resource of the title, to check a title with a placeholder.
   * @param titleArgs format arguments of the title string, empty for a plain title.
   */
  private fun setContent(
      state: SeeGeneralMediasUIState<MediaItem.Audio> = SeeGeneralMediasUIState.Loading,
      onRetry: () -> Unit = {},
      onErrorShown: () -> Unit = {},
      onBackClick: () -> Unit = {},
      onTabSelected: (Tab) -> Unit = {},
      titleRes: Int = R.string.seeAudiosTitle,
      titleArgs: List<Any> = emptyList(),
  ) {
    uiState.value = state
    composeTestRule.setContent {
      CompositionLocalProvider(LocalViewModelStoreOwner provides mediaCardOwner) {
        AmberAppTheme(dynamicColor = false) {
          SeeGeneralMedias(
              uiState = uiState.value,
              config = config.copy(titleRes = titleRes),
              onRetry = onRetry,
              onErrorShown = onErrorShown,
              onBackClick = onBackClick,
              onTabSelected = onTabSelected,
              titleArgs = titleArgs,
          ) { item, itemCircle, modifier ->
            receivedCircle = itemCircle
            Text(text = "item ${item.id}", modifier = modifier.testTag(Tags.item(item.id)))
          }
        }
      }
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Static parts
  // ---------------------------------------------------------------------------------------------

  @Test
  fun title_and_navigation_menus_are_displayed() {
    setContent()

    composeTestRule
        .onNodeWithTag(Tags.TITLE)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.seeAudiosTitle))
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU)
        .assertIsDisplayed()
  }

  @Test
  fun title_includes_the_supplied_name() {
    setContent(
        titleRes = R.string.patientHomeGreeting,
        titleArgs = listOf("Alice"),
    )

    composeTestRule
        .onNodeWithTag(Tags.TITLE)
        .assertTextEquals(string(R.string.patientHomeGreeting, "Alice"))
  }

  @Test
  @Config(qualifiers = "fr")
  fun title_includes_the_supplied_name_in_french() {
    setContent(
        titleRes = R.string.patientHomeGreeting,
        titleArgs = listOf("Alice"),
    )

    composeTestRule.onNodeWithTag(Tags.TITLE).assertTextEquals("Bonjour Alice\u00A0!")
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
  fun loading_shows_the_loader_and_nothing_else() {
    setContent(SeeGeneralMediasUIState.Loading)

    composeTestRule.onNodeWithTag(Tags.LOADING).assertIsDisplayed()
    composeTestRule.onNodeWithTag(Tags.ERROR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(Tags.EMPTY).assertDoesNotExist()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertDoesNotExist()
  }

  // ---------------------------------------------------------------------------------------------
  // Blocking error
  // ---------------------------------------------------------------------------------------------

  @Test
  fun blocking_error_shows_the_message_and_the_retry_button() {
    var retries = 0
    setContent(
        SeeGeneralMediasUIState.BlockingError(R.string.seeAudiosErrorLoadMedia),
        onRetry = { retries++ },
    )

    composeTestRule
        .onNodeWithTag(Tags.ERROR)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.seeAudiosErrorLoadMedia))
    composeTestRule.onNodeWithText(string(R.string.seeAudiosRetry)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(Tags.LOADING).assertDoesNotExist()
    composeTestRule.onNodeWithTag(Tags.EMPTY).assertDoesNotExist()

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).performClick()

    assertEquals(1, retries)
  }

  // ---------------------------------------------------------------------------------------------
  // Empty
  // ---------------------------------------------------------------------------------------------

  @Test
  fun empty_shows_the_empty_message() {
    setContent(SeeGeneralMediasUIState.Empty(profile, circle))

    composeTestRule
        .onNodeWithTag(Tags.EMPTY)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.seeAudiosEmpty))
    composeTestRule.onNodeWithTag(Tags.ERROR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(Tags.card("a1")).assertDoesNotExist()
  }

  // ---------------------------------------------------------------------------------------------
  // Content
  // ---------------------------------------------------------------------------------------------

  @Test
  fun content_shows_each_item_followed_by_its_card() {
    setContent(SeeGeneralMediasUIState.Content(profile, circle, listOf(first, second)))

    composeTestRule.onNodeWithTag(Tags.item("a1")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(Tags.card("a1")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(Tags.item("a2")).assertExists()
    composeTestRule.onNodeWithTag(Tags.card("a2")).assertExists()
    composeTestRule.onNodeWithTag(Tags.EMPTY).assertDoesNotExist()
    composeTestRule.onNodeWithTag(Tags.ERROR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(Tags.LOADING).assertDoesNotExist()
  }

  @Test
  fun item_content_receives_the_care_circle_of_the_state() {
    setContent(SeeGeneralMediasUIState.Content(profile, circle, listOf(first)))

    composeTestRule.waitForIdle()

    assertEquals(circle, receivedCircle)
  }

  @Test
  fun content_updates_when_the_state_changes() {
    setContent(SeeGeneralMediasUIState.Content(profile, circle, listOf(first, second)))
    composeTestRule.onNodeWithTag(Tags.item("a1")).assertIsDisplayed()

    composeTestRule.runOnIdle {
      uiState.value = SeeGeneralMediasUIState.Content(profile, circle, listOf(second))
    }
    composeTestRule.onNodeWithTag(Tags.item("a1")).assertDoesNotExist()
    composeTestRule.onNodeWithTag(Tags.item("a2")).assertIsDisplayed()

    composeTestRule.runOnIdle { uiState.value = SeeGeneralMediasUIState.Empty(profile, circle) }
    composeTestRule.onNodeWithTag(Tags.item("a2")).assertDoesNotExist()
    composeTestRule.onNodeWithTag(Tags.EMPTY).assertIsDisplayed()
  }

  // ---------------------------------------------------------------------------------------------
  // Non-blocking errors (Toast)
  // ---------------------------------------------------------------------------------------------

  @Test
  fun no_toast_and_no_callback_without_an_error() {
    var shown = 0
    setContent(
        SeeGeneralMediasUIState.Content(profile, circle, listOf(first)),
        onErrorShown = { shown++ },
    )

    composeTestRule.waitForIdle()

    assertNull(ShadowToast.getTextOfLatestToast())
    assertEquals(0, shown)
  }

  @Test
  fun error_on_empty_is_shown_in_a_toast_without_hiding_the_screen() {
    var shown = 0
    setContent(
        SeeGeneralMediasUIState.Empty(
            profile,
            circle,
            errorMsgRes = R.string.seeAudiosErrorLoadMedia,
        ),
        onErrorShown = { shown++ },
    )

    composeTestRule.waitForIdle()

    assertEquals(string(R.string.seeAudiosErrorLoadMedia), ShadowToast.getTextOfLatestToast())
    assertEquals(1, shown)
    composeTestRule.onNodeWithTag(Tags.EMPTY).assertIsDisplayed()
    composeTestRule.onNodeWithTag(Tags.ERROR).assertDoesNotExist()
  }

  @Test
  fun error_on_content_is_shown_in_a_toast_and_keeps_the_items() {
    var shown = 0
    setContent(
        SeeGeneralMediasUIState.Content(
            profile,
            circle,
            listOf(first),
            errorMsgRes = R.string.seeAudiosErrorLoadMedia,
        ),
        onErrorShown = { shown++ },
    )

    composeTestRule.waitForIdle()

    assertEquals(string(R.string.seeAudiosErrorLoadMedia), ShadowToast.getTextOfLatestToast())
    assertEquals(1, shown)
    composeTestRule.onNodeWithTag(Tags.item("a1")).assertIsDisplayed()
  }

  // ---------------------------------------------------------------------------------------------
  // Language
  // ---------------------------------------------------------------------------------------------

  @Test
  @Config(qualifiers = "fr")
  fun screen_is_shown_in_french() {
    setContent(SeeGeneralMediasUIState.Empty(profile, circle))

    composeTestRule.onNodeWithTag(Tags.TITLE).assertTextEquals("Voir les audios")
    composeTestRule.onNodeWithTag(Tags.EMPTY).assertTextEquals("Il n'y a pas encore d'audios.")
  }
}
