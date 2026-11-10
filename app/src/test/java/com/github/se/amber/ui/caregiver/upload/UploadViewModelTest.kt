// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.upload

import com.github.se.amber.R
import com.github.se.amber.ui.caregiver.upload.UploadUIState
import com.github.se.amber.ui.caregiver.upload.UploadViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class UploadViewModelTest {
  private companion object {
    val FIRST_ERROR = R.string.uploadMediaErrorUploadFailed
    val SECOND_ERROR = R.string.uploadMediaErrorStillLoading
  }

  private lateinit var viewModel: UploadViewModel

  @Before
  fun setUp() {
    viewModel = UploadViewModel()
  }

  // ---------- UploadUIState ----------
  @Test
  fun uiStateHasNoErrorByDefault() {
    assertNull(UploadUIState().errorMsgRes)
  }

  @Test
  fun uiStateKeepsGivenErrorMessage() {
    assertEquals(FIRST_ERROR, UploadUIState(errorMsgRes = FIRST_ERROR).errorMsgRes)
  }

  @Test
  fun uiStateCopyCanClearErrorMessage() {
    assertNull(UploadUIState(errorMsgRes = FIRST_ERROR).copy(errorMsgRes = null).errorMsgRes)
  }

  // ---------- setErrorMsg ----------
  @Test
  fun setErrorMsgStoresTheMessage() {
    viewModel.setErrorMsg(FIRST_ERROR)
    assertEquals(FIRST_ERROR, viewModel.uiState.value.errorMsgRes)
  }

  @Test
  fun setErrorMsgReplacesPreviousMessage() {
    viewModel.setErrorMsg(FIRST_ERROR)
    viewModel.setErrorMsg(SECOND_ERROR)
    assertEquals(SECOND_ERROR, viewModel.uiState.value.errorMsgRes)
  }

  // ---------- clearErrorMsg ----------
  @Test
  fun clearErrorMsgRemovesTheMessage() {
    viewModel.setErrorMsg(FIRST_ERROR)
    viewModel.clearErrorMsg()
    assertNull(viewModel.uiState.value.errorMsgRes)
  }

  @Test
  fun clearErrorMsgWithoutErrorKeepsStateWithoutError() {
    viewModel.clearErrorMsg()
    assertNull(viewModel.uiState.value.errorMsgRes)
  }

  @Test
  fun clearErrorMsgTwiceStillLeavesNoError() {
    viewModel.setErrorMsg(FIRST_ERROR)
    viewModel.clearErrorMsg()
    viewModel.clearErrorMsg()
    assertNull(viewModel.uiState.value.errorMsgRes)
  }
}
