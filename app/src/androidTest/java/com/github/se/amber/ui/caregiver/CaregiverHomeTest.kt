/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 * Modified by Alaabrine, with assistance from Cursor.
 */
package com.github.se.amber.ui.caregiver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.caregiver.CaregiverHomeTestTags.WELCOME_TEXT
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CaregiverHomeScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var viewModel: CaregiverHomeViewModel
  private lateinit var uiStateFlow: MutableStateFlow<CaregiverHomeUIState>

  private val caregiver =
      UserProfile(uid = "cg1", role = Role.CAREGIVER, circleIds = listOf("circle1"))
  private val patient =
      UserProfile(uid = "p1", role = Role.PATIENT, person = Person(firstName = "Arthur"))
  private val circle = CareCircle(id = "circle1", patientId = "p1")

  @Before
  fun setUp() {
    uiStateFlow =
        MutableStateFlow(
            CaregiverHomeUIState(
                caregiver = caregiver,
                careCircle = circle,
                patient = patient,
            )
        )

    viewModel = mockk(relaxed = true)

    every { viewModel.uiState } returns uiStateFlow
  }

  private fun setScreen() {
    composeTestRule.setContent { CaregiverHomeScreen(viewModel = viewModel) }
    composeTestRule.waitForIdle()
  }

  @Test
  fun title_isDisplayed() {
    setScreen()
    composeTestRule.onNodeWithTag(CaregiverHomeTestTags.TITLE).assertIsDisplayed()
  }

  @Test
  fun welcomeText_isDisplayed() {
    setScreen()
    composeTestRule.onNodeWithTag(CaregiverHomeTestTags.WELCOME_TEXT).assertIsDisplayed()
  }

  @Test
  fun welcomeText_showsNoPatientMessage_whenPatientUidIsBlank() {
    uiStateFlow.value = uiStateFlow.value.copy(patient = UserProfile())
    setScreen()
    composeTestRule
        .onNodeWithTag(CaregiverHomeTestTags.WELCOME_TEXT)
        .assertTextEquals("Welcome! There are not patient related to this carecircle yet.")
  }

  @Test
  fun welcomeText_showsPatientFirstName() {
    setScreen()
    composeTestRule
        .onNodeWithTag(CaregiverHomeTestTags.WELCOME_TEXT)
        .assertTextEquals("Welcome ! You are with Arthur")
  }

  @Test
  fun bottomNavigationMenu_isDisplayed() {
    setScreen()
    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU)
        .assertIsDisplayed()
  }

  @Test
  fun launchedEffect_whenErrorMsgIsSet_clearErrorMsgIsCalled() {
    composeTestRule.setContent { CaregiverHomeScreen(viewModel = viewModel) }

    viewModel.setErrorMsg("Test error")
    composeTestRule.mainClock.advanceTimeByFrame()
    assertTrue(viewModel.uiState.value.errorMsg == null)
  }
}
