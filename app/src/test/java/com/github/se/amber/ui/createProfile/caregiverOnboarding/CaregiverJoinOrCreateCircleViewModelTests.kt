// this code was written by Alaabrine
// with the aid of an AI coding agent
package com.github.se.amber.ui.createProfile.caregiverOnboarding

import com.github.se.amber.ui.accountCreation.caregiverOnboarding.CareCircleEntryChoice
import com.github.se.amber.ui.accountCreation.caregiverOnboarding.CaregiverJoinOrCreateCircleUiState
import com.github.se.amber.ui.accountCreation.caregiverOnboarding.CaregiverJoinOrCreateCircleViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [CaregiverJoinOrCreateCircleViewModel].
 *
 * Covers the caregiver "Choice between" onboarding step: create a care circle vs join via
 * invitation. Enter-invitation and create-circle destinations are owned by navigation, not this
 * ViewModel.
 */
class CaregiverJoinOrCreateCircleViewModelTests {

  // ---------- Initial state ----------

  @Test
  fun initialState_defaultsToEmptyDisplayNameAndNoChoice() {
    val viewModel = CaregiverJoinOrCreateCircleViewModel()

    assertEquals(CaregiverJoinOrCreateCircleUiState(), viewModel.uiState.value)
    assertEquals(CareCircleEntryChoice.None, viewModel.uiState.value.choice)
  }

  @Test
  fun initialState_exposesProvidedDisplayName() {
    val viewModel = CaregiverJoinOrCreateCircleViewModel(displayName = DISPLAY_NAME)

    assertEquals(DISPLAY_NAME, viewModel.uiState.value.displayName)
    assertEquals(CareCircleEntryChoice.None, viewModel.uiState.value.choice)
  }

  // ---------- Create (→ CreateCareCircle) ----------

  @Test
  fun onCreateCircleClick_setsChoiceToCreate() {
    val viewModel = CaregiverJoinOrCreateCircleViewModel(displayName = DISPLAY_NAME)

    viewModel.onCreateCircleClick()

    assertEquals(CareCircleEntryChoice.Create, viewModel.uiState.value.choice)
  }

  @Test
  fun onCreateCircleClick_preservesDisplayName() {
    val viewModel = CaregiverJoinOrCreateCircleViewModel(displayName = DISPLAY_NAME)

    viewModel.onCreateCircleClick()

    assertEquals(DISPLAY_NAME, viewModel.uiState.value.displayName)
  }

  @Test
  fun onCreateCircleClick_repeated_staysCreate() {
    val viewModel = CaregiverJoinOrCreateCircleViewModel(displayName = DISPLAY_NAME)

    viewModel.onCreateCircleClick()
    viewModel.onCreateCircleClick()

    assertEquals(CareCircleEntryChoice.Create, viewModel.uiState.value.choice)
  }

  // ---------- Join (→ EnterInvitation; destination not implemented here) ----------

  @Test
  fun onJoinCircleClick_setsChoiceToJoin() {
    val viewModel = CaregiverJoinOrCreateCircleViewModel(displayName = DISPLAY_NAME)

    viewModel.onJoinCircleClick()

    assertEquals(CareCircleEntryChoice.Join, viewModel.uiState.value.choice)
  }

  @Test
  fun onJoinCircleClick_preservesDisplayName() {
    val viewModel = CaregiverJoinOrCreateCircleViewModel(displayName = DISPLAY_NAME)

    viewModel.onJoinCircleClick()

    assertEquals(DISPLAY_NAME, viewModel.uiState.value.displayName)
  }

  @Test
  fun onJoinCircleClick_repeated_staysJoin() {
    val viewModel = CaregiverJoinOrCreateCircleViewModel(displayName = DISPLAY_NAME)

    viewModel.onJoinCircleClick()
    viewModel.onJoinCircleClick()

    assertEquals(CareCircleEntryChoice.Join, viewModel.uiState.value.choice)
  }

  // ---------- Switching between buttons ----------

  @Test
  fun onJoinCircleClick_afterCreate_overwritesChoiceToJoin() {
    val viewModel = CaregiverJoinOrCreateCircleViewModel(displayName = DISPLAY_NAME)

    viewModel.onCreateCircleClick()
    viewModel.onJoinCircleClick()

    assertEquals(CareCircleEntryChoice.Join, viewModel.uiState.value.choice)
    assertEquals(DISPLAY_NAME, viewModel.uiState.value.displayName)
  }

  @Test
  fun onCreateCircleClick_afterJoin_overwritesChoiceToCreate() {
    val viewModel = CaregiverJoinOrCreateCircleViewModel(displayName = DISPLAY_NAME)

    viewModel.onJoinCircleClick()
    viewModel.onCreateCircleClick()

    assertEquals(CareCircleEntryChoice.Create, viewModel.uiState.value.choice)
    assertEquals(DISPLAY_NAME, viewModel.uiState.value.displayName)
  }

  // ---------- Choice consumed (after navigation) ----------

  @Test
  fun onChoiceHandled_clearsCreateChoice() {
    val viewModel = CaregiverJoinOrCreateCircleViewModel(displayName = DISPLAY_NAME)

    viewModel.onCreateCircleClick()
    viewModel.onChoiceHandled()

    assertEquals(CareCircleEntryChoice.None, viewModel.uiState.value.choice)
    assertEquals(DISPLAY_NAME, viewModel.uiState.value.displayName)
  }

  @Test
  fun onChoiceHandled_clearsJoinChoice() {
    val viewModel = CaregiverJoinOrCreateCircleViewModel(displayName = DISPLAY_NAME)

    viewModel.onJoinCircleClick()
    viewModel.onChoiceHandled()

    assertEquals(CareCircleEntryChoice.None, viewModel.uiState.value.choice)
  }

  @Test
  fun onChoiceHandled_whenNone_isNoOp() {
    val viewModel = CaregiverJoinOrCreateCircleViewModel(displayName = DISPLAY_NAME)

    viewModel.onChoiceHandled()

    assertEquals(CareCircleEntryChoice.None, viewModel.uiState.value.choice)
  }

  private companion object {
    const val DISPLAY_NAME = "Sarah"
  }
}
