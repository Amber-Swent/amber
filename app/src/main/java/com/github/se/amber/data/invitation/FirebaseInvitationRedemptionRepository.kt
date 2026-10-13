// This file was written by Neha Chakraborty and Copilot, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.invitation

import com.github.se.amber.model.user.Role
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/** Firebase Functions-backed implementation of [InvitationRedemptionRepository]. */
class FirebaseInvitationRedemptionRepository(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
) : InvitationRedemptionRepository {

  override suspend fun redeemInvitation(code: String): Result<InvitationRedemptionResult> {
    if (!INVITATION_CODE_REGEX.matches(code)) {
      return Result.failure(InvitationError.InvalidCode)
    }

    return try {
      val result =
          functions
              .getHttpsCallable(REDEEM_INVITATION_FUNCTION)
              .call(mapOf(INVITATION_CODE_KEY to code))
              .await()

      result.data.toInvitationRedemptionResult()
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: FirebaseFunctionsException) {
      Result.failure(exception.toInvitationError())
    } catch (exception: Exception) {
      Result.failure(InvitationError.Unknown(exception))
    }
  }

  private fun Any?.toInvitationRedemptionResult(): Result<InvitationRedemptionResult> {
    val response = this as? Map<*, *> ?: return malformedResponse()
    val careCircleId = response[CARE_CIRCLE_ID_KEY] as? String
    val roleName = response[ROLE_KEY] as? String
    val role = Role.entries.firstOrNull { it.name == roleName }

    return if (careCircleId.isNullOrBlank() || role == null) {
      malformedResponse()
    } else {
      Result.success(InvitationRedemptionResult(careCircleId = careCircleId, role = role))
    }
  }

  private fun malformedResponse(): Result<Nothing> =
      Result.failure(
          InvitationError.Unknown(
              IllegalStateException("redeemInvitation returned an invalid response"),
          ),
      )

  private fun FirebaseFunctionsException.toInvitationError(): InvitationError =
      when (code) {
        FirebaseFunctionsException.Code.INVALID_ARGUMENT -> InvitationError.InvalidCode
        FirebaseFunctionsException.Code.NOT_FOUND -> InvitationError.InvitationNotFound
        FirebaseFunctionsException.Code.UNAVAILABLE,
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> InvitationError.Network
        FirebaseFunctionsException.Code.FAILED_PRECONDITION -> toPreconditionError()
        else -> InvitationError.Unknown(this)
      }

  /** Maps the backend's machine-readable `details.reason`; the message is for logs only. */
  private fun FirebaseFunctionsException.toPreconditionError(): InvitationError =
      when ((details as? Map<*, *>)?.get(REASON_KEY)) {
        "INVITATION_ALREADY_REDEEMED" -> InvitationError.InvitationAlreadyUsed
        "INVITATION_EXPIRED" -> InvitationError.InvitationExpired
        "ALREADY_CIRCLE_MEMBER" -> InvitationError.UserAlreadyBelongsToCircle
        "PROFILE_INVALID" -> InvitationError.MissingOrInvalidUserProfile
        "PATIENT_SLOT_TAKEN" -> InvitationError.PatientSlotAlreadyOccupied
        "CIRCLE_UNAVAILABLE" -> InvitationError.InvalidOrUnavailableCareCircle
        else -> InvitationError.Unknown(this)
      }

  private companion object {
    val INVITATION_CODE_REGEX = Regex("^[A-Za-z0-9_-]{1,128}$")
    const val REDEEM_INVITATION_FUNCTION = "redeemInvitation"
    const val INVITATION_CODE_KEY = "code"
    const val CARE_CIRCLE_ID_KEY = "circleId"
    const val ROLE_KEY = "role"
    const val REASON_KEY = "reason"
  }
}
