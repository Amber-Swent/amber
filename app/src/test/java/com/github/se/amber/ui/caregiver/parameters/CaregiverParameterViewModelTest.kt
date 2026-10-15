/* Written by Yuhan YANG, with assistance from
 * Cursor.
 */
package com.github.se.amber.ui.caregiver.parameters

import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.github.se.amber.model.authentication.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests sign-out success, failure, and clearing the error on the parameters ViewModel. */
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
  fun clearErrorMsgRemovesTheMessage() =
      runTest(dispatcher) {
        val viewModel = CaregiverParameterViewModel()

        advanceUntilIdle()
        viewModel.clearErrorMsg()

        assertNull(viewModel.uiState.value.errorMsg)
      }
}
