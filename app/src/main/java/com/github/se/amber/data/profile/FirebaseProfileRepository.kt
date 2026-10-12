// This file was written by Neha Chakraborty and Copilot
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

typealias ProfileCreationError = FirebaseProfileRepository.ProfileCreationError

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

  /** Typed failures returned while creating a profile. */
  sealed class ProfileCreationError(message: String) : Exception(message) {
    data object InvalidPersonDetails : ProfileCreationError("A first name is required")

    data object NotAuthenticated : ProfileCreationError("No user is signed in")

    data object ProfileAlreadyExists :
        ProfileCreationError("A profile already exists for this user")

    data object PermissionDenied :
        ProfileCreationError("You do not have permission to create a profile")

    data object Network : ProfileCreationError("Unable to create a profile while offline")

    data class Unknown(val source: Throwable) : ProfileCreationError("Unable to create a profile")
  }

  private companion object {
    const val USERS_COLLECTION = "users"
  }
}
