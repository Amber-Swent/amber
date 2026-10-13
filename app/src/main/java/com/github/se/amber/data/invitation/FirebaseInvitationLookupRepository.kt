// This file was written by Neha Chakraborty and Copilot
package com.github.se.amber.data.invitation

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Firebase-backed implementation that retrieves a caregiver's invitations for one care circle.
 *
 * A malformed invitation makes the complete lookup fail rather than silently omitting it.
 */
class FirebaseInvitationLookupRepository(
    private val auth: FirebaseAuth = Firebase.auth,
    private val firestore: FirebaseFirestore = Firebase.firestore,
    private val timeProvider: () -> Long = System::currentTimeMillis,
) : InvitationLookupRepository {

  override suspend fun getInvitations(selectedCircleId: String): Result<List<InvitationSummary>> {
    val creatorUid =
        auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("No Firebase user is signed in"))

    return try {
      val invitationSnapshots =
          firestore
              .collection(INVITATIONS_COLLECTION)
              .whereEqualTo(CREATED_BY_FIELD, creatorUid)
              .whereEqualTo(CIRCLE_ID_FIELD, selectedCircleId)
              .get()
              .await()
              .documents
      val currentTimeMillis = timeProvider()
      val invitations =
          invitationSnapshots
              .map { document -> document.toInvitationSummary(currentTimeMillis) }
              .sortedByDescending(InvitationSummary::createdAt)

      Result.success(invitations)
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: Exception) {
      Result.failure(exception)
    }
  }

  private fun DocumentSnapshot.toInvitationSummary(currentTimeMillis: Long): InvitationSummary {
    val createdAt = getLong(CREATED_AT_FIELD) ?: invalidInvitation()
    val expiresAt = getLong(EXPIRES_AT_FIELD) ?: invalidInvitation()
    val usedBy = get(USED_BY_FIELD)
    if (usedBy != null && usedBy !is String) {
      invalidInvitation()
    }

    val status =
        when {
          usedBy is String -> InvitationStatus.USED
          expiresAt <= currentTimeMillis -> InvitationStatus.EXPIRED
          else -> InvitationStatus.ACTIVE
        }

    return InvitationSummary(id, createdAt, expiresAt, status)
  }

  private fun DocumentSnapshot.invalidInvitation(): Nothing =
      throw IllegalStateException("Invitation $id has an invalid format")

  private companion object {
    const val INVITATIONS_COLLECTION = "invitations"
    const val CREATED_BY_FIELD = "createdBy"
    const val CIRCLE_ID_FIELD = "circleId"
    const val CREATED_AT_FIELD = "createdAt"
    const val EXPIRES_AT_FIELD = "expiresAt"
    const val USED_BY_FIELD = "usedBy"
  }
}
