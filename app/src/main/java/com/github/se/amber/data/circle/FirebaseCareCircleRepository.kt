// This file was written by Neha Chakraborty and Claude Code
package com.github.se.amber.data.circle

import com.github.se.amber.model.circle.CareCircle
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.firestore
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/** Firebase-backed implementation of [CareCircleRepository]. */
class FirebaseCareCircleRepository(
    private val firestore: FirebaseFirestore = Firebase.firestore,
) : CareCircleRepository {

  override suspend fun getCircle(circleId: String): Result<CareCircle> {
    if (circleId.isBlank()) {
      return Result.failure(CareCircleError.InvalidCircleId)
    }

    return try {
      val snapshot = firestore.collection(CARE_CIRCLES_COLLECTION).document(circleId).get().await()
      val circle =
          snapshot.takeIf { it.exists() }?.toObject(CareCircle::class.java)
              ?: return Result.failure(CareCircleError.NotFound)
      Result.success(circle)
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: FirebaseFirestoreException) {
      Result.failure(exception.toCareCircleError())
    } catch (exception: Exception) {
      Result.failure(CareCircleError.Unknown(exception))
    }
  }

  private fun FirebaseFirestoreException.toCareCircleError(): CareCircleError =
      when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> CareCircleError.PermissionDenied
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> CareCircleError.NotAuthenticated
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> CareCircleError.Network
        else -> CareCircleError.Unknown(this)
      }

  private companion object {
    const val CARE_CIRCLES_COLLECTION = "careCircles"
  }
}
