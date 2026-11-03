// this code was written with the aid of AI
package com.github.se.amber.ui.patient.seeMedias

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    assertNull(viewModel.uiState.value.errorMsg)
    assertEquals(SeeMediasUIState(), viewModel.uiState.value)
  }

  @Test
  fun setErrorMsg_updatesUiState() {
    viewModel.setErrorMsg("Something went wrong")

    assertEquals("Something went wrong", viewModel.uiState.value.errorMsg)
  }

  @Test
  fun setErrorMsg_overwritesPreviousError() {
    viewModel.setErrorMsg("First error")
    viewModel.setErrorMsg("Second error")

    assertEquals("Second error", viewModel.uiState.value.errorMsg)
  }

  @Test
  fun setErrorMsg_acceptsEmptyString() {
    viewModel.setErrorMsg("")

    assertNotNull(viewModel.uiState.value.errorMsg)
    assertEquals("", viewModel.uiState.value.errorMsg)
  }

  @Test
  fun clearErrorMsg_resetsErrorToNull() {
    viewModel.setErrorMsg("Some error")

    viewModel.clearErrorMsg()

    assertNull(viewModel.uiState.value.errorMsg)
  }

  @Test
  fun clearErrorMsg_withoutPriorError_keepsStateUnchanged() {
    viewModel.clearErrorMsg()

    assertNull(viewModel.uiState.value.errorMsg)
    assertEquals(SeeMediasUIState(), viewModel.uiState.value)
  }

  @Test
  fun setThenClearThenSet_worksAsExpected() {
    viewModel.setErrorMsg("Error 1")
    viewModel.clearErrorMsg()
    assertNull(viewModel.uiState.value.errorMsg)

    viewModel.setErrorMsg("Error 2")
    assertEquals("Error 2", viewModel.uiState.value.errorMsg)
  }

  @Test
  fun uiState_exposesSameStateFlowInstance() {
    assertSame(viewModel.uiState, viewModel.uiState)
  }

  @Test
  fun uiStateDataClass_copyAndEquality() {
    val state = SeeMediasUIState(errorMsg = "err")

    assertEquals(SeeMediasUIState(errorMsg = "err"), state)
    assertEquals(SeeMediasUIState(errorMsg = null), state.copy(errorMsg = null))
    assertEquals("err", state.component1())
  }
}
