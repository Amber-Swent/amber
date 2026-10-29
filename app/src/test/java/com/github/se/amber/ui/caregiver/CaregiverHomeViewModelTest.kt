/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 * Modified by Alaabrine, with assistance from Cursor.
 */
package com.github.se.amber.ui.caregiver

import com.github.se.amber.model.user.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaregiverHomeViewModelTest {
  @Test
  fun homeUIState_patientRoleIsPatient() {
    assertEquals(Role.PATIENT, CaregiverHomeViewModel().uiState.value.patient.role)
  }

  @Test
  fun clearErrorMsg_resetsTheError() {
    val viewModel = CaregiverHomeViewModel().apply { setErrorMsg("boom") }

    assertEquals("boom", viewModel.uiState.value.errorMsg)

    viewModel.clearErrorMsg()

    assertNull(viewModel.uiState.value.errorMsg)
  }
}
