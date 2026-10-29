// Written by Alaabrine, with assistance from Cursor.
package com.github.se.amber.ui.patient.parameters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PatientParameterViewModelTest {
  @Test
  fun clearErrorMsg_resetsTheError() {
    val viewModel = PatientParameterViewModel().apply { setErrorMsg("boom") }

    assertEquals("boom", viewModel.uiState.value.errorMsg)

    viewModel.clearErrorMsg()

    assertNull(viewModel.uiState.value.errorMsg)
  }
}
