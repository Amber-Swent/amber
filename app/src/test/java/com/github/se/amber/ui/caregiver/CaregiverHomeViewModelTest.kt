package com.github.se.amber.ui.caregiver

import com.github.se.amber.model.user.Role
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CaregiverHomeViewModelTest {
  private lateinit var viewModel: CaregiverHomeViewModel

  @Before
  fun setUp() {
    viewModel = CaregiverHomeViewModel()
  }

  @Test
  fun homeUIState_patientRoleIsPatient() {
    assertEquals(Role.PATIENT, viewModel.uiState.value.patient.role)
  }
}
