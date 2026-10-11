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

/** Firebase-backed implementation of [ProfileRepository]. */
class FirebaseProfileRepository(
    private val auth: FirebaseAuth = Firebase.auth,
    private val firestore: FirebaseFirestore = Firebase.firestore,
) : ProfileRepository {

  override suspend fun createProfile(person: Person, role: Role): Result<Unit> {
    if (person.firstName.isBlank()) {
      return Result.failure(ProfileCreationError.InvalidPersonDetails)
    }

    val uid = auth.currentUser?.uid ?: return Result.failure(ProfileCreationError.NotAuthenticated)
    val profileReference = firestore.collection(USERS_COLLECTION).document(uid)
    val profile =
        UserProfile(
            uid = uid,
            role = role,
            person = person.copy(id = uid),
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
    } catch (error: ProfileCreationError) {
      Result.failure(error)
    } catch (exception: FirebaseFirestoreException) {
      Result.failure(exception.toProfileCreationError())
    } catch (exception: Exception) {
      Result.failure(ProfileCreationError.Unknown(exception))
    }
  }

  private fun FirebaseFirestoreException.toProfileCreationError(): ProfileCreationError =
      when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> ProfileCreationError.PermissionDenied
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> ProfileCreationError.NotAuthenticated
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> ProfileCreationError.Network
        else -> ProfileCreationError.Unknown(this)
      }

  private companion object {
    const val USERS_COLLECTION = "users"
  }
}
