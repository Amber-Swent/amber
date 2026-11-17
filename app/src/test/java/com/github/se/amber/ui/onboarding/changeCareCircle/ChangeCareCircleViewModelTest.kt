// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.onboarding.changeCareCircle

import org.junit.Assert.assertEquals
import org.junit.Test

class ChangeCareCircleViewModelTest {
  @Test
  fun startsLoading() {
    val state = ChangeCareCircleViewModel().uiState.value

    assertEquals(ChangeCareCircleStatus.LOADING, state.status)
    assertEquals(emptyList<ChangeCareCircleChoice>(), state.circles)
  }

  @Test
  fun exposesProvidedState() {
    val initialState =
        ChangeCareCircleUIState(
            status = ChangeCareCircleStatus.READY,
            circles = listOf(ChangeCareCircleChoice("circle", "Family")),
            activeCircleId = "circle",
        )

    assertEquals(initialState, ChangeCareCircleViewModel(initialState).uiState.value)
  }
}
