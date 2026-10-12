// This file was written by Neha Chakraborty and Copilot
package com.github.se.amber.data.onboarding

import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.firestore
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/** Firebase-backed implementation of [OnboardingRepository]. */
class FirebaseOnboardingRepository(
    private val auth: FirebaseAuth = Firebase.auth,
    private val firestore: FirebaseFirestore = Firebase.firestore,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : OnboardingRepository {

  override suspend fun createInitialCareCircle(name: String): Result<CareCircle> {
    val circleName = name.trim()
    if (circleName.isEmpty()) {
      return Result.failure(OnboardingError.InvalidCircleName)
    }

    val uid = auth.currentUser?.uid ?: return Result.failure(OnboardingError.NotAuthenticated)
    val profileReference = firestore.collection(USERS_COLLECTION).document(uid)
    val circleReference = firestore.collection(CARE_CIRCLES_COLLECTION).document()

    return try {
      val circle =
          firestore
              .runTransaction { transaction ->
                val profileSnapshot = transaction.get(profileReference)
                val profile = profileSnapshot.toObject(UserProfile::class.java)

                when {
                  !profileSnapshot.exists() -> throw OnboardingError.ProfileMissing
                  profile == null ||
                      !profileSnapshot.hasValidProfileFields() ||
                      profile.uid != uid ||
                      profile.person.id != uid -> throw OnboardingError.InvalidProfile
                  profile.role != Role.CAREGIVER -> throw OnboardingError.NotCaregiver
                  profile.circleIds.isNotEmpty() -> throw OnboardingError.CareCircleAlreadyExists
                }

                CareCircle(
                        id = circleReference.id,
                        name = circleName,
                        memberIds = listOf(uid),
                        createdBy = uid,
                        createdAt = currentTimeMillis(),
                    )
                    .also { createdCircle ->
                      transaction.set(circleReference, createdCircle)
                      transaction.update(
                          profileReference,
                          CIRCLE_IDS_FIELD,
                          FieldValue.arrayUnion(circleReference.id),
                      )
                    }
              }
              .await()
      Result.success(circle)
    } catch (exception: CancellationException) {
      throw exception
    } catch (error: OnboardingError) {
      Result.failure(error)
    } catch (exception: FirebaseFirestoreException) {
      Result.failure(exception.toOnboardingError())
    } catch (exception: Exception) {
      Result.failure(OnboardingError.Unknown(exception))
    }
  }

  private fun com.google.firebase.firestore.DocumentSnapshot.hasValidProfileFields() =
      contains(UID_FIELD) &&
          contains(ROLE_FIELD) &&
          contains(PERSON_FIELD) &&
          contains(CIRCLE_IDS_FIELD)

  private fun FirebaseFirestoreException.toOnboardingError(): OnboardingError =
      when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> OnboardingError.PermissionDenied
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> OnboardingError.NotAuthenticated
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> OnboardingError.Network
        else -> OnboardingError.Unknown(this)
      }

  private companion object {
    const val USERS_COLLECTION = "users"
    const val CARE_CIRCLES_COLLECTION = "careCircles"
    const val UID_FIELD = "uid"
    const val ROLE_FIELD = "role"
    const val PERSON_FIELD = "person"
    const val CIRCLE_IDS_FIELD = "circleIds"
  }
}
