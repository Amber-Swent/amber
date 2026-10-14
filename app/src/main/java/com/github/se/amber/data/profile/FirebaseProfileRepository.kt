// This file was written by Neha Chakraborty and Copilot
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.profile

import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.firestore
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/** Firebase-backed implementation of [ProfileRepository]. */
class FirebaseProfileRepository(
    private val auth: FirebaseAuth = Firebase.auth,
    private val firestore: FirebaseFirestore = Firebase.firestore,
) : ProfileRepository {

  override suspend fun createProfile(person: Person, role: Role): Result<Unit> {
    val cleanedPerson =
        person.copy(
            firstName = person.firstName.trim(),
            lastName = person.lastName.trim(),
        )
    if (cleanedPerson.firstName.isBlank()) {
      return Result.failure(ProfileCreationError.InvalidPersonDetails)
    }

    val uid = auth.currentUser?.uid ?: return Result.failure(ProfileCreationError.NotAuthenticated)
    val profileReference = firestore.collection(USERS_COLLECTION).document(uid)
    val profile =
        UserProfile(
            uid = uid,
            role = role,
            person = cleanedPerson.copy(id = uid),
            circleIds = emptyList(),
        )

    return try {
      firestore
          .runTransaction { transaction ->
            if (transaction.get(profileReference).exists()) {
              throw ProfileCreationError.ProfileAlreadyExists
            }
            transaction.set(profileReference, profile)
          }
          .await()
      Result.success(Unit)
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: Exception) {
      Result.failure(exception.toProfileCreationError())
    }
  }

  override suspend fun getCurrentProfile(): Result<UserProfile?> {
    val uid = auth.currentUser?.uid ?: return Result.failure(ProfileReadError.NotAuthenticated)
    return getProfile(uid)
  }

  override suspend fun getProfile(uid: String): Result<UserProfile?> {
    // e.g. CareCircle.patientId before the patient joins; document("") would throw
    if (uid.isBlank()) return Result.success(null)

    return try {
      val snapshot = firestore.collection(USERS_COLLECTION).document(uid).get().await()
      when {
        !snapshot.exists() -> Result.success(null)
        // toObject() would silently fall back to the default role
        !snapshot.contains(ROLE_FIELD) -> Result.failure(ProfileReadError.InvalidProfile)
        else -> {
          val profile =
              try {
                snapshot.toObject(UserProfile::class.java)
              } catch (exception: RuntimeException) {
                // the stored fields don't match UserProfile, e.g. an unknown role name
                null
              }
          profile?.let { Result.success(it) } ?: Result.failure(ProfileReadError.InvalidProfile)
        }
      }
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: Exception) {
      Result.failure(exception.toProfileReadError())
    }
  }

  private fun Exception.toProfileReadError(): ProfileReadError =
      when (this) {
        is FirebaseFirestoreException ->
            when (code) {
              FirebaseFirestoreException.Code.PERMISSION_DENIED -> ProfileReadError.PermissionDenied
              FirebaseFirestoreException.Code.UNAUTHENTICATED -> ProfileReadError.NotAuthenticated
              FirebaseFirestoreException.Code.UNAVAILABLE,
              FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> ProfileReadError.Network
              else -> ProfileReadError.Unknown(this)
            }
        else -> ProfileReadError.Unknown(this)
      }

  private fun Exception.toProfileCreationError(): ProfileCreationError =
      when (this) {
        is ProfileCreationError -> this
        is FirebaseFirestoreException -> toProfileCreationError()
        else -> ProfileCreationError.Unknown(this)
      }

  private fun FirebaseFirestoreException.toProfileCreationError(): ProfileCreationError =
      when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> ProfileCreationError.PermissionDenied
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> ProfileCreationError.NotAuthenticated
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        FirebaseFirestoreException.Code.ABORTED -> ProfileCreationError.Network
        FirebaseFirestoreException.Code.ALREADY_EXISTS -> ProfileCreationError.ProfileAlreadyExists
        else -> ProfileCreationError.Unknown(this)
      }

  private companion object {
    const val USERS_COLLECTION = "users"
    val ROLE_FIELD = UserProfile::role.name
  }
}
