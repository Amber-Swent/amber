// This file was written by Neha Chakraborty and Copilot
package com.github.se.amber.data.invitation

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.firestore
import java.security.SecureRandom
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/** Firebase-backed implementation that creates collision-resistant universal invitation codes. */
class FirebaseInvitationCreationRepository(
    private val auth: FirebaseAuth = Firebase.auth,
    private val firestore: FirebaseFirestore = Firebase.firestore,
    private val timeProvider: () -> Long = System::currentTimeMillis,
    private val codeGenerator: () -> String = ::generateSecureCode,
    private val maximumCreateAttempts: Int = DEFAULT_MAXIMUM_CREATE_ATTEMPTS,
) : InvitationCreationRepository {

  init {
    require(maximumCreateAttempts > 0) { "maximumCreateAttempts must be positive" }
  }

  override suspend fun createInvitation(selectedCircleId: String): InvitationCreationResult {
    val creatorUid = auth.currentUser?.uid ?: return InvitationCreationResult.NotAuthenticated
    val createdAt = timeProvider()
    val expiresAt = calculateExpiry(createdAt) ?: return InvitationCreationResult.InvalidExpiry

    repeat(maximumCreateAttempts) {
      val code = codeGenerator()
      val invitationPayload =
          universalInvitationPayload(
              code = code,
              circleId = selectedCircleId,
              createdBy = creatorUid,
              createdAt = createdAt,
              expiresAt = expiresAt,
          )

      try {
        val invitationReference = firestore.collection(INVITATIONS_COLLECTION).document(code)
        firestore
            .runTransaction { transaction ->
              if (transaction.get(invitationReference).exists()) {
                throw InvitationCodeCollisionException()
              }
              transaction.set(invitationReference, invitationPayload)
            }
            .await()
        return InvitationCreationResult.Created(code)
      } catch (_: InvitationCodeCollisionException) {
        return@repeat
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: FirebaseFirestoreException) {
        if (exception.code == FirebaseFirestoreException.Code.ALREADY_EXISTS) {
          return@repeat
        }
        return exception.toInvitationCreationResult()
      } catch (exception: Exception) {
        return InvitationCreationResult.FirestoreFailure(exception)
      }
    }

    return InvitationCreationResult.CollisionRetryLimitExceeded
  }

  private fun calculateExpiry(createdAt: Long): Long? =
      try {
        Math.addExact(createdAt, INVITATION_LIFETIME_MILLIS).takeIf { it > createdAt }
      } catch (_: ArithmeticException) {
        null
      }

  private fun FirebaseFirestoreException.toInvitationCreationResult(): InvitationCreationResult =
      when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            InvitationCreationResult.PermissionDenied
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> InvitationCreationResult.NotAuthenticated
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> InvitationCreationResult.NetworkFailure
        else -> InvitationCreationResult.FirestoreFailure(this)
      }

  private companion object {
    const val INVITATIONS_COLLECTION = "invitations"
    const val DEFAULT_MAXIMUM_CREATE_ATTEMPTS = 5
    const val INVITATION_LIFETIME_MILLIS = 30L * 24 * 60 * 60 * 1_000
  }
}

internal fun universalInvitationPayload(
    code: String,
    circleId: String,
    createdBy: String,
    createdAt: Long,
    expiresAt: Long,
): Map<String, Any?> =
    mapOf(
        "code" to code,
        "circleId" to circleId,
        "createdBy" to createdBy,
        "createdAt" to createdAt,
        "expiresAt" to expiresAt,
        "usedBy" to null,
    )

private const val INVITATION_CODE_LENGTH = 8
private const val INVITATION_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
private val secureRandom = SecureRandom()

private class InvitationCodeCollisionException : Exception()

private fun generateSecureCode(): String =
    buildString(INVITATION_CODE_LENGTH) {
      repeat(INVITATION_CODE_LENGTH) {
        append(INVITATION_CODE_ALPHABET[secureRandom.nextInt(INVITATION_CODE_ALPHABET.length)])
      }
    }
