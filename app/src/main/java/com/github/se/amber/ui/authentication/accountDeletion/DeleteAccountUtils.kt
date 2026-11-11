// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.authentication.accountDeletion

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.github.se.amber.data.account.AccountDeletionRepository
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.authentication.signOut.PreviewSignOutAuthRepository
import com.github.se.amber.ui.theme.AmberAppTheme

/** Local data source for the delete-account preview. */
private object PreviewDeleteAccountRepository :
    AccountDeletionRepository, ProfileRepository, CareCircleRepository {
  override suspend fun deleteAccount() = Result.success(Unit)

  override suspend fun clearLocalData() = Result.success(Unit)

  override suspend fun clearGoneAccount() = Result.success(Unit)

  override suspend fun finishInterruptedDeletion() = Unit

  override suspend fun createProfile(person: Person, role: Role) = Result.success(Unit)

  override suspend fun getCurrentProfile(): Result<UserProfile?> =
      Result.success(UserProfile(uid = "preview-caregiver", role = Role.CAREGIVER))

  override suspend fun getProfile(uid: String): Result<UserProfile?> = Result.success(null)

  override fun newId() = "preview-circle"

  override suspend fun getCircle(circleId: String): Result<CareCircle> =
      Result.success(CareCircle())

  override suspend fun addPerson(circleId: String, person: Person): Result<Person> =
      Result.success(person)
}

internal val previewDeleteAccountViewModel =
    DeleteAccountViewModel(
        deletionRepository = PreviewDeleteAccountRepository,
        authRepository = PreviewSignOutAuthRepository,
        profileRepository = PreviewDeleteAccountRepository,
        circleRepository = PreviewDeleteAccountRepository,
    )

/** Preview of [DeleteAccountScreen] without Firebase access. */
@Preview(showBackground = true, showSystemUi = true)
@Composable
internal fun DeleteAccountScreenPreview() {
  AmberAppTheme(dynamicColor = false) {
    DeleteAccountScreen(viewModel = previewDeleteAccountViewModel)
  }
}
