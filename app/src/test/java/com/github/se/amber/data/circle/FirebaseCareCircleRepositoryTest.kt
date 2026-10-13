// This file was written by Neha Chakraborty and Claude Code
package com.github.se.amber.data.circle

import com.github.se.amber.model.circle.CareCircle
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class FirebaseCareCircleRepositoryTest {
  private val circleId = "circle-456"
  private val circle =
      CareCircle(
          id = circleId,
          name = "Family",
          memberIds = listOf("caregiver-123"),
          createdBy = "caregiver-123",
          createdAt = 1_700_000_000_000L,
      )

  private lateinit var firestore: FirebaseFirestore
  private lateinit var circles: CollectionReference
  private lateinit var circleDocument: DocumentReference
  private lateinit var repository: FirebaseCareCircleRepository

  @Before
  fun setUp() {
    firestore = mockk()
    circles = mockk()
    circleDocument = mockk()

    every { firestore.collection("careCircles") } returns circles
    every { circles.document(circleId) } returns circleDocument

    repository = FirebaseCareCircleRepository(firestore)
  }

  @Test
  fun getCircle_returnsStoredCircle() = runTest {
    stubSnapshot(circleSnapshot(circle))

    val result = repository.getCircle(circleId)

    assertEquals(circle, result.getOrThrow())
    verify(exactly = 1) { circles.document(circleId) }
  }

  @Test
  fun getCircle_takesIdFromDocumentIdRatherThanStoredFields() = runTest {
    stubSnapshot(circleSnapshot(circle.copy(id = "")))

    val result = repository.getCircle(circleId)

    assertEquals(circleId, result.getOrThrow().id)
  }

  @Test
  fun getCircle_rejectsBlankIdBeforeAccessingFirestore() = runTest {
    val result = repository.getCircle("  ")

    assertFailure<CareCircleError.InvalidCircleId>(result)
    verify(exactly = 0) { firestore.collection(any()) }
  }

  @Test
  fun getCircle_rejectsUntrimmedIdBeforeAccessingFirestore() = runTest {
    val result = repository.getCircle(" $circleId ")

    assertFailure<CareCircleError.InvalidCircleId>(result)
    verify(exactly = 0) { firestore.collection(any()) }
  }

  @Test
  fun getCircle_rejectsIdWithSlashBeforeAccessingFirestore() = runTest {
    val result = repository.getCircle("$circleId/media/m1")

    assertFailure<CareCircleError.InvalidCircleId>(result)
    verify(exactly = 0) { firestore.collection(any()) }
  }

  @Test
  fun getCircle_reportsMissingDocumentAsNotFound() = runTest {
    stubSnapshot(circleSnapshot(circle = null, exists = false))

    val result = repository.getCircle(circleId)

    assertFailure<CareCircleError.NotFound>(result)
  }

  @Test
  fun getCircle_reportsNullMappedObjectAsNotFound() = runTest {
    stubSnapshot(circleSnapshot(circle = null))

    val result = repository.getCircle(circleId)

    assertFailure<CareCircleError.NotFound>(result)
  }

  @Test
  fun getCircle_wrapsDecodingFailure() = runTest {
    val source = RuntimeException("Could not deserialize object")
    stubSnapshot(
        mockk {
          every { exists() } returns true
          every { toObject(CareCircle::class.java) } throws source
        }
    )

    val result = repository.getCircle(circleId)

    assertUnknownFailure(result, source)
  }

  @Test
  fun getCircle_rethrowsCancellationFromCancelledTask() = runTest {
    // A cancelled task, so the cancellation comes from await() rather than from get() itself.
    val cancelled =
        mockk<Task<DocumentSnapshot>> {
          every { isComplete } returns true
          every { isCanceled } returns true
          every { exception } returns null
        }
    every { circleDocument.get() } returns cancelled

    try {
      repository.getCircle(circleId)
      fail("Cancellation should be rethrown")
    } catch (_: CancellationException) {}
  }

  @Test
  fun getCircle_mapsNotFoundFirestoreFailure() = runTest {
    stubFailure(FirebaseFirestoreException("missing", FirebaseFirestoreException.Code.NOT_FOUND))

    val result = repository.getCircle(circleId)

    assertFailure<CareCircleError.NotFound>(result)
  }

  @Test
  fun getCircle_mapsPermissionDeniedFirestoreFailure() = runTest {
    stubFailure(
        FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
    )

    val result = repository.getCircle(circleId)

    assertFailure<CareCircleError.PermissionDenied>(result)
  }

  @Test
  fun getCircle_mapsUnauthenticatedFirestoreFailure() = runTest {
    stubFailure(
        FirebaseFirestoreException("signed out", FirebaseFirestoreException.Code.UNAUTHENTICATED)
    )

    val result = repository.getCircle(circleId)

    assertFailure<CareCircleError.NotAuthenticated>(result)
  }

  @Test
  fun getCircle_mapsUnavailableFirestoreFailureToNetwork() = runTest {
    stubFailure(FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE))

    val result = repository.getCircle(circleId)

    assertFailure<CareCircleError.Network>(result)
  }

  @Test
  fun getCircle_mapsDeadlineExceededFirestoreFailureToNetwork() = runTest {
    stubFailure(
        FirebaseFirestoreException("timed out", FirebaseFirestoreException.Code.DEADLINE_EXCEEDED)
    )

    val result = repository.getCircle(circleId)

    assertFailure<CareCircleError.Network>(result)
  }

  @Test
  fun getCircle_wrapsUnexpectedFirestoreFailure() = runTest {
    val source = FirebaseFirestoreException("failed", FirebaseFirestoreException.Code.INTERNAL)
    stubFailure(source)

    val result = repository.getCircle(circleId)

    assertUnknownFailure(result, source)
  }

  @Test
  fun getCircle_wrapsUnexpectedFailure() = runTest {
    val source = IllegalStateException("failed")
    stubFailure(source)

    val result = repository.getCircle(circleId)

    assertUnknownFailure(result, source)
  }

  private fun stubSnapshot(snapshot: DocumentSnapshot) {
    every { circleDocument.get() } returns Tasks.forResult(snapshot)
  }

  private fun stubFailure(error: Exception) {
    every { circleDocument.get() } returns Tasks.forException(error)
  }

  private fun circleSnapshot(circle: CareCircle?, exists: Boolean = true): DocumentSnapshot =
      mockk {
        every { this@mockk.exists() } returns exists
        every { id } returns circleId
        every { toObject(CareCircle::class.java) } returns circle
      }

  private inline fun <reified T : Throwable> assertFailure(result: Result<CareCircle>) {
    assertFalse(result.isSuccess)
    assertTrue(result.exceptionOrNull() is T)
  }

  private fun assertUnknownFailure(result: Result<CareCircle>, source: Throwable) {
    assertFalse(result.isSuccess)
    val error = result.exceptionOrNull()
    assertTrue(error is CareCircleError.Unknown)
    assertSame(source, (error as CareCircleError.Unknown).source)
  }
}
