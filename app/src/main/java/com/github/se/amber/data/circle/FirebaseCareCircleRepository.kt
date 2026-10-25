// This file was written by Neha Chakraborty and Claude Code
// Modified with assistance from OpenAI Codex

package com.github.se.amber.data.circle

import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.user.Person
import com.google.firebase.Firebase
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.firestore
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/** Firebase-backed implementation of [CareCircleRepository]. */
class FirebaseCareCircleRepository(
    private val firestore: FirebaseFirestore = Firebase.firestore,
) : CareCircleRepository {

  override fun newId(): String = newId(firestore.collection(CARE_CIRCLES_COLLECTION))

  override suspend fun getCircle(circleId: String): Result<CareCircle> {
    if (!isValidId(circleId)) {
      return Result.failure(CareCircleError.InvalidCircleId)
    }

    return circleCall {
      val circles = firestore.collection(CARE_CIRCLES_COLLECTION)
      val snapshot = circles.document(circleId).get().await()
      val circle =
          snapshot.takeIf { it.exists() }?.toObject(CareCircle::class.java)
              ?: return@circleCall Result.failure(CareCircleError.NotFound)

      Result.success(circle.copy(id = snapshot.id))
    }
  }

  override suspend fun addPerson(circleId: String, person: Person): Result<Person> {
    if (!isValidId(circleId)) {
      return Result.failure(CareCircleError.InvalidCircleId)
    }

    if (!person.hasValidName()) {
      return Result.failure(CareCircleError.InvalidPersonName)
    }

    return circleCall {
      val circles = firestore.collection(CARE_CIRCLES_COLLECTION)
      val circleDocument = circles.document(circleId)
      val personId = newId(circles)
      val savedPerson =
          person.copy(
              id = personId,
              firstName = person.firstName.trim(),
              lastName = person.lastName.trim(),
              nickname = person.nickname?.trim()?.takeIf { it.isNotEmpty() },
          )

      // Writes one entry to CareCircle.people
      circleDocument.update(FieldPath.of("people", personId), savedPerson).await()
      Result.success(savedPerson)
    }
  }

  // To be used any time the same error catching pattern arises
  private suspend fun <T> circleCall(
      block: suspend () -> Result<T>,
  ): Result<T> =
      try {
        block()
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: FirebaseFirestoreException) {
        Result.failure(exception.toCareCircleError())
      } catch (exception: Exception) {
        Result.failure(CareCircleError.Unknown(exception))
      }

  private fun newId(collection: CollectionReference): String = collection.document().id

  // A '/' would make document() resolve to a nested path and read a different document.
  private fun isValidId(id: String): Boolean = id.isNotBlank() && id == id.trim() && '/' !in id

  private fun FirebaseFirestoreException.toCareCircleError(): CareCircleError =
      when (code) {
        FirebaseFirestoreException.Code.NOT_FOUND -> CareCircleError.NotFound
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
