package com.github.se.amber.ui.caregiver

import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.user.Role
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) :
    TestWatcher() {
  override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

  override fun finished(description: Description) = Dispatchers.resetMain()
}

class CaregiverHomeViewModelTest {
  @get:Rule val mainRule = MainDispatcherRule()

  private lateinit var authRepo: AuthRepository

  @Before
  fun setup() {
    authRepo = mockk(relaxed = true)
    mockkStatic(Log::class)
    every { Log.e(any(), any(), any()) } returns 0
  }

  @After fun tearDown() = unmockkAll()

  @Test
  fun homeUIState_patientRoleIsPatient() {
    assertEquals(Role.PATIENT, CaregiverHomeViewModel(authRepo).uiState.value.patient.role)
  }

  @Test
  fun signOutSuccessSetsSignedOut_andClearsCredentials() = runTest {
    coEvery { authRepo.signOut() } returns Result.success(Unit)
    val credentialManager = mockk<CredentialManager>(relaxed = true)

    val vm = CaregiverHomeViewModel(authRepo)
    vm.signOut(credentialManager)

    assertTrue(vm.uiState.value.signedOut)
    coVerify { credentialManager.clearCredentialState(any<ClearCredentialStateRequest>()) }
  }

  @Test
  fun signOutFailureSetsError_andDoesNotSignOut() = runTest {
    coEvery { authRepo.signOut() } returns Result.failure(Exception("nope"))
    val credentialManager = mockk<CredentialManager>(relaxed = true)

    val vm = CaregiverHomeViewModel(authRepo)
    vm.signOut(credentialManager)

    assertEquals("nope", vm.uiState.value.errorMsg)
    assertFalse(vm.uiState.value.signedOut)
    coVerify(exactly = 0) {
      credentialManager.clearCredentialState(any<ClearCredentialStateRequest>())
    }
  }
}
