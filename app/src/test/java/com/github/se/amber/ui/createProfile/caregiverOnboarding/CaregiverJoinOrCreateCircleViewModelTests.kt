// this code was written by Alaabrine
// with the aid of an AI coding agent
package com.github.se.amber.ui.createProfile.caregiverOnboarding

import com.github.se.amber.ui.accountCreation.caregiverOnboarding.CaregiverJoinOrCreateCircleUiState
import com.github.se.amber.ui.accountCreation.caregiverOnboarding.CaregiverJoinOrCreateCircleViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for [CaregiverJoinOrCreateCircleViewModel]. */
class CaregiverJoinOrCreateCircleViewModelTests {

  @Test
  fun initialState_defaultsToEmptyDisplayName() {
    val viewModel = CaregiverJoinOrCreateCircleViewModel()

    assertEquals(CaregiverJoinOrCreateCircleUiState(), viewModel.uiState.value)
  }

  @Test
  fun initialState_exposesProvidedDisplayName() {
    val viewModel = CaregiverJoinOrCreateCircleViewModel(displayName = DISPLAY_NAME)

    assertEquals(DISPLAY_NAME, viewModel.uiState.value.displayName)
  }

  private companion object {
    const val DISPLAY_NAME = "Sarah"
  }
}
