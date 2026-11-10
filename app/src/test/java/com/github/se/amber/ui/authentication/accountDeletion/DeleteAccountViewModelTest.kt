// Written by MeiliV, with assistance from OpenAI Codex.
package com.github.se.amber.ui.authentication.accountDeletion

import android.content.Context
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.github.se.amber.R
import com.github.se.amber.data.account.AccountDeletionError
import com.github.se.amber.data.account.AccountDeletionRepository
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.ReauthenticationError
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteAccountViewModelTest {
  private val deletionRepository = mockk<AccountDeletionRepository>()
  private val authRepository = mockk<AuthRepository>()
  private val profileRepository = mockk<ProfileRepository>()
  private val circleRepository = mockk<CareCircleRepository>()
  private val context = mockk<Context>()
  private val credentialManager = mockk<CredentialManager>()
  private lateinit var viewModel: DeleteAccountViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher())
    every { context.getString(R.string.default_web_client_id) } returns "client-id"
    coEvery { profileRepository.getCurrentProfile() } returns
        Result.success(UserProfile(uid = "caregiver", circleIds = listOf("circle")))
    coEvery { circleRepository.getCircle("circle") } returns
        Result.success(CareCircle(memberIds = listOf("caregiver", "other")))
    viewModel =
        DeleteAccountViewModel(
            deletionRepository,
            authRepository,
            profileRepository,
            circleRepository,
        )
  }

  @After fun tearDown() = Dispatchers.resetMain()

  private fun stubCredential(): Credential {
    val credential = mockk<Credential>()
    val response = mockk<GetCredentialResponse>()
    every { response.credential } returns credential
    coEvery { credentialManager.getCredential(context, any<GetCredentialRequest>()) } returns
        response
    return credential
  }

  @Test
  fun initialStateRequiresWarningsBeforeDeletion() {
    assertTrue(viewModel.uiState.value.isLoading)
    assertFalse(viewModel.uiState.value.warningsLoaded)
    viewModel.deleteAccount(context, credentialManager)
    coVerify(exactly = 0) { deletionRepository.deleteAccount() }
  }

  @Test
  fun ordinaryFamilyHasNoWarningAndIsLoadedOnce() = runTest {
    viewModel.loadWarnings()
    advanceUntilIdle()
    viewModel.loadWarnings()

    assertTrue(viewModel.uiState.value.warnings.isEmpty())
    assertTrue(viewModel.uiState.value.warningsLoaded)
    coVerify(exactly = 1) { profileRepository.getCurrentProfile() }
  }

  @Test
  fun concurrentWarningLoadsReadTheProfileOnlyOnce() = runTest {
    val pending = CompletableDeferred<Result<UserProfile?>>()
    coEvery { profileRepository.getCurrentProfile() } coAnswers { pending.await() }

    viewModel.loadWarnings()
    runCurrent()
    viewModel.loadWarnings()
    coVerify(exactly = 1) { profileRepository.getCurrentProfile() }

    pending.complete(Result.success(UserProfile(uid = "caregiver", circleIds = listOf("circle"))))
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.warningsLoaded)
  }

  @Test
  fun lastMemberWarningIsLoadedBeforeConfirmation() = runTest {
    coEvery { circleRepository.getCircle("circle") } returns
        Result.success(CareCircle(memberIds = listOf("caregiver")))

    viewModel.loadWarnings()
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.warningsLoaded)
    assertEquals(R.string.deleteAccountLastMemberWarning, viewModel.uiState.value.warnings.single())
  }

  @Test
  fun lastCaregiverWarningIsLoaded() = runTest {
    coEvery { circleRepository.getCircle("circle") } returns
        Result.success(
            CareCircle(patientId = "patient", memberIds = listOf("caregiver", "patient"))
        )

    viewModel.loadWarnings()
    advanceUntilIdle()

    assertEquals(
        R.string.deleteAccountLastCaregiverWarning,
        viewModel.uiState.value.warnings.single(),
    )
  }

  @Test
  fun warningsAcrossFamiliesAreDeduplicated() = runTest {
    coEvery { profileRepository.getCurrentProfile() } returns
        Result.success(UserProfile(uid = "caregiver", circleIds = listOf("one", "two", "three")))
    coEvery { circleRepository.getCircle("one") } returns
        Result.success(CareCircle(memberIds = listOf("caregiver")))
    coEvery { circleRepository.getCircle("two") } returns
        Result.success(CareCircle(memberIds = listOf("caregiver")))
    coEvery { circleRepository.getCircle("three") } returns
        Result.success(
            CareCircle(patientId = "patient", memberIds = listOf("caregiver", "patient"))
        )

    viewModel.loadWarnings()
    advanceUntilIdle()

    assertEquals(
        listOf(
            R.string.deleteAccountLastMemberWarning,
            R.string.deleteAccountLastCaregiverWarning,
        ),
        viewModel.uiState.value.warnings,
    )
    coVerify(exactly = 1) { circleRepository.getCircle("one") }
    coVerify(exactly = 1) { circleRepository.getCircle("two") }
    coVerify(exactly = 1) { circleRepository.getCircle("three") }
  }

  @Test
  fun patientProfileNeverEnablesDeletion() = runTest {
    coEvery { profileRepository.getCurrentProfile() } returns
        Result.success(UserProfile(uid = "patient", role = Role.PATIENT))

    viewModel.loadWarnings()
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.warningsLoaded)
    viewModel.deleteAccount(context, credentialManager)
    coVerify(exactly = 0) { deletionRepository.deleteAccount() }
  }

  @Test
  fun failedWarningReadCanBeRetried() = runTest {
    coEvery { circleRepository.getCircle("circle") } returns Result.failure(Exception("offline"))
    viewModel.loadWarnings()
    advanceUntilIdle()
    assertFalse(viewModel.uiState.value.warningsLoaded)

    coEvery { circleRepository.getCircle("circle") } returns
        Result.success(CareCircle(memberIds = listOf("caregiver", "other")))
    viewModel.loadWarnings()
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.warningsLoaded)
  }

  @Test
  fun successfulReauthenticationCallsDeletionOnce() = runTest {
    viewModel.loadWarnings()
    advanceUntilIdle()
    val credential = stubCredential()
    coEvery { authRepository.reauthenticateWithGoogle(credential) } returns Result.success(Unit)
    coEvery { deletionRepository.deleteAccount() } returns Result.success(Unit)

    viewModel.deleteAccount(context, credentialManager)
    advanceUntilIdle()

    coVerify(exactly = 1) { authRepository.reauthenticateWithGoogle(credential) }
    coVerify(exactly = 1) { deletionRepository.deleteAccount() }
    assertTrue(viewModel.uiState.value.isDeleting)
  }

  @Test
  fun repeatTapDuringDeletionDoesNotStartAnotherRequest() = runTest {
    viewModel.loadWarnings()
    advanceUntilIdle()
    val credential = stubCredential()
    val pending = CompletableDeferred<Result<Unit>>()
    coEvery { authRepository.reauthenticateWithGoogle(credential) } returns Result.success(Unit)
    coEvery { deletionRepository.deleteAccount() } coAnswers { pending.await() }

    viewModel.deleteAccount(context, credentialManager)
    runCurrent()
    viewModel.deleteAccount(context, credentialManager)
    coVerify(exactly = 1) { deletionRepository.deleteAccount() }

    pending.complete(Result.success(Unit))
    advanceUntilIdle()
  }

  @Test
  fun wrongGoogleAccountDoesNotCallDeletion() = runTest {
    viewModel.loadWarnings()
    advanceUntilIdle()
    val credential = stubCredential()
    coEvery { authRepository.reauthenticateWithGoogle(credential) } returns
        Result.failure(ReauthenticationError.WrongAccount)

    viewModel.deleteAccount(context, credentialManager)
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isDeleting)
    assertEquals(R.string.deleteAccountWrongAccountError, viewModel.uiState.value.errorMessageRes)
    coVerify(exactly = 0) { deletionRepository.deleteAccount() }
  }

  @Test
  fun reauthenticationNetworkFailureKeepsDeletionAvailableForRetry() = runTest {
    viewModel.loadWarnings()
    advanceUntilIdle()
    val credential = stubCredential()
    coEvery { authRepository.reauthenticateWithGoogle(credential) } returns
        Result.failure(ReauthenticationError.Network)

    viewModel.deleteAccount(context, credentialManager)
    advanceUntilIdle()

    assertEquals(R.string.deleteAccountNetworkError, viewModel.uiState.value.errorMessageRes)
    assertFalse(viewModel.uiState.value.isDeleting)
    assertFalse(viewModel.uiState.value.localCleanupNeeded)
    coVerify(exactly = 0) { deletionRepository.deleteAccount() }
  }

  @Test
  fun localCleanupFailureRetriesCleanupWithoutReauthentication() = runTest {
    viewModel.loadWarnings()
    advanceUntilIdle()
    val credential = stubCredential()
    coEvery { authRepository.reauthenticateWithGoogle(credential) } returns Result.success(Unit)
    coEvery { deletionRepository.deleteAccount() } returns
        Result.failure(AccountDeletionError.LocalDataNotCleared(Exception("disk")))
    coEvery { deletionRepository.clearLocalData() } returns Result.success(Unit)

    viewModel.deleteAccount(context, credentialManager)
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.localCleanupNeeded)
    viewModel.deleteAccount(context, credentialManager)
    advanceUntilIdle()

    coVerify(exactly = 1) { deletionRepository.deleteAccount() }
    coVerify(exactly = 1) { deletionRepository.clearLocalData() }
    coVerify(exactly = 1) { authRepository.reauthenticateWithGoogle(credential) }
  }

  @Test
  fun accountNotFoundClearsLocalData() = runTest {
    viewModel.loadWarnings()
    advanceUntilIdle()
    val credential = stubCredential()
    coEvery { authRepository.reauthenticateWithGoogle(credential) } returns
        Result.failure(ReauthenticationError.AccountNotFound)
    coEvery { deletionRepository.clearLocalData() } returns Result.success(Unit)

    viewModel.deleteAccount(context, credentialManager)
    advanceUntilIdle()

    coVerify(exactly = 1) { deletionRepository.clearLocalData() }
    coVerify(exactly = 0) { deletionRepository.deleteAccount() }
  }

  @Test
  fun networkFailureAllowsASecondDeletionAttempt() = runTest {
    viewModel.loadWarnings()
    advanceUntilIdle()
    val credential = stubCredential()
    coEvery { authRepository.reauthenticateWithGoogle(credential) } returns Result.success(Unit)
    coEvery { deletionRepository.deleteAccount() } returns
        Result.failure(AccountDeletionError.Network)

    viewModel.deleteAccount(context, credentialManager)
    advanceUntilIdle()
    assertEquals(R.string.deleteAccountNetworkError, viewModel.uiState.value.errorMessageRes)
    assertFalse(viewModel.uiState.value.isDeleting)

    coEvery { deletionRepository.deleteAccount() } returns Result.success(Unit)
    viewModel.deleteAccount(context, credentialManager)
    advanceUntilIdle()
    coVerify(exactly = 2) { deletionRepository.deleteAccount() }
  }

  @Test
  fun reauthenticationRequiredAsksGoogleAgainOnRetry() = runTest {
    viewModel.loadWarnings()
    advanceUntilIdle()
    val credential = stubCredential()
    coEvery { authRepository.reauthenticateWithGoogle(credential) } returns Result.success(Unit)
    coEvery { deletionRepository.deleteAccount() } returns
        Result.failure(AccountDeletionError.ReauthenticationRequired)

    viewModel.deleteAccount(context, credentialManager)
    advanceUntilIdle()
    assertEquals(
        R.string.deleteAccountReauthenticationRequiredError,
        viewModel.uiState.value.errorMessageRes,
    )

    coEvery { deletionRepository.deleteAccount() } returns Result.success(Unit)
    viewModel.deleteAccount(context, credentialManager)
    advanceUntilIdle()
    coVerify(exactly = 2) { authRepository.reauthenticateWithGoogle(credential) }
  }

  @Test
  fun cancelledGooglePickerShowsNoErrorAndDoesNotDelete() = runTest {
    viewModel.loadWarnings()
    advanceUntilIdle()
    coEvery { credentialManager.getCredential(context, any<GetCredentialRequest>()) } throws
        GetCredentialCancellationException()

    viewModel.deleteAccount(context, credentialManager)
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isDeleting)
    assertNull(viewModel.uiState.value.errorMessageRes)
    coVerify(exactly = 0) { deletionRepository.deleteAccount() }
  }

  @Test
  fun unexpectedCredentialPickerFailureShowsRetryableError() = runTest {
    viewModel.loadWarnings()
    advanceUntilIdle()
    coEvery { credentialManager.getCredential(context, any<GetCredentialRequest>()) } throws
        IllegalStateException("Credential provider unavailable")

    viewModel.deleteAccount(context, credentialManager)
    advanceUntilIdle()

    assertEquals(R.string.deleteAccountUnknownError, viewModel.uiState.value.errorMessageRes)
    assertFalse(viewModel.uiState.value.isDeleting)
    assertFalse(viewModel.uiState.value.localCleanupNeeded)
    coVerify(exactly = 0) { deletionRepository.deleteAccount() }
  }

  @Test
  fun accountNotFoundCleanupFailureCanRetryWithoutGoogle() = runTest {
    viewModel.loadWarnings()
    advanceUntilIdle()
    val credential = stubCredential()
    coEvery { authRepository.reauthenticateWithGoogle(credential) } returns
        Result.failure(ReauthenticationError.AccountNotFound)
    coEvery { deletionRepository.clearLocalData() } returns
        Result.failure(AccountDeletionError.LocalDataNotCleared(Exception("disk")))

    viewModel.deleteAccount(context, credentialManager)
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.localCleanupNeeded)
    assertFalse(viewModel.uiState.value.isDeleting)

    coEvery { deletionRepository.clearLocalData() } returns Result.success(Unit)
    viewModel.deleteAccount(context, credentialManager)
    advanceUntilIdle()
    coVerify(exactly = 2) { deletionRepository.clearLocalData() }
    coVerify(exactly = 1) { authRepository.reauthenticateWithGoogle(credential) }
  }

  @Test
  fun clearErrorDoesNotEnableDeletion() = runTest {
    coEvery { circleRepository.getCircle("circle") } returns Result.failure(Exception("offline"))
    viewModel.loadWarnings()
    advanceUntilIdle()
    assertEquals(R.string.deleteAccountCheckFamiliesError, viewModel.uiState.value.errorMessageRes)

    viewModel.clearError()

    assertNull(viewModel.uiState.value.errorMessageRes)
    assertFalse(viewModel.uiState.value.warningsLoaded)
  }
}
