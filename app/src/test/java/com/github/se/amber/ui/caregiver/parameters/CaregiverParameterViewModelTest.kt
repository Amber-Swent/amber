// Written by Yuhan YANG, with assistance from Cursor.
// Modify by Lou-Anne Maier, with assistance from Claude Code
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver.parameters

import com.github.se.amber.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Tests the caregiver parameters viewModel */
@OptIn(ExperimentalCoroutinesApi::class)
class CaregiverParameterViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun clearErrorMsgRemovesAnExistingMessage() =
      runTest(dispatcher) {
        val viewModel = CaregiverParameterViewModel()
        viewModel.setErrorMsg(R.string.signOutError)

        viewModel.clearErrorMsg()

        assertNull(viewModel.uiState.value.errorMsgRes)
      }

  @Test
  fun setErrorMsgExposesTheMessage() =
      runTest(dispatcher) {
        val viewModel = CaregiverParameterViewModel()

        viewModel.setErrorMsg(R.string.signOutError)

        assertEquals(R.string.signOutError, viewModel.uiState.value.errorMsgRes)
      }
}
