// Written by Alaabrine, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.patient.parameters

import com.github.se.amber.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PatientParameterViewModelTest {
  @Test
  fun clearErrorMsg_resetsTheError() {
    val viewModel = PatientParameterViewModel().apply { setErrorMsg(R.string.signOutError) }

    assertEquals(R.string.signOutError, viewModel.uiState.value.errorMsgRes)

    viewModel.clearErrorMsg()

    assertNull(viewModel.uiState.value.errorMsgRes)
  }
}
