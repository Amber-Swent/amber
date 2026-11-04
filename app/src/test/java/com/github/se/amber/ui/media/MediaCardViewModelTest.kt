package com.github.se.amber.ui.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCardViewModelTest {

  @Test
  fun initialState_isEmpty() {
    val viewModel = MediaCardViewModel()
    val state = viewModel.uiState.value

    assertEquals("", state.addedByNickname)
    assertTrue(state.relatedNicknames.isEmpty())
    assertFalse(state.isLoading)
  }
}
