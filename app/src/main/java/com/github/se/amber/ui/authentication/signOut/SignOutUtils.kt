// Written by Alaabrine, with assistance from Cursor.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
// Modified by MeiliV, with assistance from OpenAI Codex.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.authentication.signOut

import androidx.credentials.Credential
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** No-op [AuthRepository] used by Compose previews (and preview-driven tests). */
internal object PreviewSignOutAuthRepository : AuthRepository {
  override val currentUser: AuthUser? = null

  override suspend fun signInWithGoogle(credential: Credential): Result<AuthUser> =
      Result.failure(UnsupportedOperationException("Preview auth repository"))

  override suspend fun reauthenticateWithGoogle(credential: Credential): Result<Unit> =
      Result.failure(UnsupportedOperationException("Preview auth repository"))

  override suspend fun signOut(): Result<Unit> = Result.success(Unit)
}

/** No-op [ActiveCircleRepository] used by Compose previews, so they never open DataStore. */
internal object PreviewActiveCircleRepository : ActiveCircleRepository {
  override fun activeCircleId(uid: String, circleIds: List<String>): Flow<String?> = flowOf(null)

  override suspend fun setActiveCircle(uid: String, id: String) = Unit

  override suspend fun clear(uid: String) = Unit
}

internal val previewSignOutViewModel =
    SignOutViewModel(
        authRepository = PreviewSignOutAuthRepository,
        activeCircleRepository = PreviewActiveCircleRepository,
    )
