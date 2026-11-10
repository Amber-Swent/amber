// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.patient.seeMedias

import com.github.se.amber.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class SeeMediasViewModelTest {

  private lateinit var viewModel: SeeMediasViewModel

  @Before
  fun setUp() {
    viewModel = SeeMediasViewModel()
  }

  @Test
  fun initialState_hasNoErrorMessage() {
    assertNull(viewModel.uiState.value.errorMsgRes)
    assertEquals(SeeMediasUIState(), viewModel.uiState.value)
  }

  @Test
  fun setErrorMsg_updatesUiState() {
    viewModel.setErrorMsg(R.string.seePicturesErrorLoadMedia)

    assertEquals(R.string.seePicturesErrorLoadMedia, viewModel.uiState.value.errorMsgRes)
  }

  @Test
  fun setErrorMsg_overwritesPreviousError() {
    viewModel.setErrorMsg(R.string.seePicturesErrorLoadMedia)
    viewModel.setErrorMsg(R.string.seePicturesErrorLoadProfile)

    assertEquals(R.string.seePicturesErrorLoadProfile, viewModel.uiState.value.errorMsgRes)
  }

  @Test
  fun clearErrorMsg_resetsErrorToNull() {
    viewModel.setErrorMsg(R.string.seePicturesErrorLoadMedia)

    viewModel.clearErrorMsg()

    assertNull(viewModel.uiState.value.errorMsgRes)
  }

  @Test
  fun clearErrorMsg_withoutPriorError_keepsStateUnchanged() {
    viewModel.clearErrorMsg()

    assertNull(viewModel.uiState.value.errorMsgRes)
    assertEquals(SeeMediasUIState(), viewModel.uiState.value)
  }

  @Test
  fun setThenClearThenSet_worksAsExpected() {
    viewModel.setErrorMsg(R.string.seePicturesErrorLoadMedia)
    viewModel.clearErrorMsg()
    assertNull(viewModel.uiState.value.errorMsgRes)

    viewModel.setErrorMsg(R.string.seePicturesErrorLoadProfile)
    assertEquals(R.string.seePicturesErrorLoadProfile, viewModel.uiState.value.errorMsgRes)
  }

  @Test
  fun uiState_exposesSameStateFlowInstance() {
    assertSame(viewModel.uiState, viewModel.uiState)
  }

  @Test
  fun uiStateDataClass_copyAndEquality() {
    val state = SeeMediasUIState(errorMsgRes = R.string.seePicturesErrorLoadMedia)

    assertEquals(SeeMediasUIState(errorMsgRes = R.string.seePicturesErrorLoadMedia), state)
    assertEquals(SeeMediasUIState(errorMsgRes = null), state.copy(errorMsgRes = null))
    assertEquals(R.string.seePicturesErrorLoadMedia, state.component1())
  }
}
