// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.patient.seeMedias.audios

import org.junit.Assert.assertEquals
import org.junit.Test

class SeeAudiosViewModelTest {
  @Test
  fun starts_in_empty_state() {
    val viewModel = SeeAudiosViewModel()

    assertEquals(SeeAudiosUIState.Empty, viewModel.uiState.value)
  }
}
