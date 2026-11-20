// Written by Neha Chakraborty and Claude Code
// Modified by MeiliV, with assistance from OpenAI Codex
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.circle

import com.github.se.amber.data.isValidDocumentId
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.location.Place
import com.github.se.amber.model.location.pointOrNull
import com.github.se.amber.model.user.Person
import com.google.firebase.Firebase
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.firestore
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

/** Firebase-backed implementation of [CareCircleRepository]. */
class FirebaseCareCircleRepository(
    private val firestore: FirebaseFirestore = Firebase.firestore,
) : CareCircleRepository {

  override fun newId(): String = newId(firestore.collection(CARE_CIRCLES_COLLECTION))

  override suspend fun getCircle(circleId: String): Result<CareCircle> {
    if (!isValidDocumentId(circleId)) {
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
    if (!isValidDocumentId(circleId)) {
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

  override fun observePlaces(circleId: String): Flow<List<Place>> {
    if (!isValidDocumentId(circleId)) {
      return flow { throw CareCircleError.InvalidCircleId }
    }

    return callbackFlow {
      val circleDocument = firestore.collection(CARE_CIRCLES_COLLECTION).document(circleId)
      val registration = circleDocument.addSnapshotListener { snapshot, error ->
        when {
          error != null -> close(error.toCareCircleError())
          snapshot != null ->
              runCatching { snapshot.toObject(CareCircle::class.java)?.places.orEmpty() }
                  .onSuccess { places ->
                    // The key is the place's id, even if the stored id field differs
                    trySend(places.map { (placeId, place) -> place.copy(id = placeId) })
                  }
                  .onFailure { close(CareCircleError.Unknown(it)) }
        }
      }
      awaitClose { registration.remove() }
    }
        .conflate()
  }

  override suspend fun addPlace(circleId: String, place: Place): Result<Place> {
    if (!isValidDocumentId(circleId)) {
      return Result.failure(CareCircleError.InvalidCircleId)
    }
    return circleCall {
      writePlace(circleId, place.copy(id = newId(firestore.collection(CARE_CIRCLES_COLLECTION))))
    }
  }

  override suspend fun updatePlace(circleId: String, place: Place): Result<Place> {
    if (!isValidDocumentId(circleId)) {
      return Result.failure(CareCircleError.InvalidCircleId)
    }
    if (!isValidDocumentId(place.id)) {
      return Result.failure(CareCircleError.InvalidPlaceId)
    }
    return circleCall { writePlace(circleId, place) }
  }

  override suspend fun deletePlace(circleId: String, placeId: String): Result<Unit> {
    if (!isValidDocumentId(circleId)) {
      return Result.failure(CareCircleError.InvalidCircleId)
    }
    if (!isValidDocumentId(placeId)) {
      return Result.failure(CareCircleError.InvalidPlaceId)
    }
    return circleCall {
      // Not awaited: the phone saves it at once and sends it once online
      firestore
          .collection(CARE_CIRCLES_COLLECTION)
          .document(circleId)
          .update(FieldPath.of("places", placeId), FieldValue.delete())
      Result.success(Unit)
    }
  }

  /** Writes one entry of CareCircle.places, without waiting for the server (see [addPlace]). */
  private fun writePlace(circleId: String, place: Place): Result<Place> {
    val name = place.name.trim()
    if (name.isEmpty() || place.location.pointOrNull() == null) {
      return Result.failure(CareCircleError.InvalidPlace)
    }
    val savedPlace = place.copy(name = name)
    firestore
        .collection(CARE_CIRCLES_COLLECTION)
        .document(circleId)
        .update(FieldPath.of("places", savedPlace.id), savedPlace)
    return Result.success(savedPlace)
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
