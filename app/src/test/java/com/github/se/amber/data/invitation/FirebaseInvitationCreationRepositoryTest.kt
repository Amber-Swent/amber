// This file was written by Neha Chakraborty and Copilot
package com.github.se.amber.data.invitation

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Transaction
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FirebaseInvitationCreationRepositoryTest {
  private val uid = "caregiver-123"
  private val circleId = "circle-456"
  private val createdAt = 1_700_000_000_000L

  private lateinit var auth: FirebaseAuth
  private lateinit var firestore: FirebaseFirestore
  private lateinit var collection: CollectionReference
  private lateinit var document: DocumentReference
  private lateinit var user: FirebaseUser
  private lateinit var transaction: Transaction

  @Before
  fun setUp() {
    auth = mockk()
    firestore = mockk()
    collection = mockk()
    document = mockk()
    user = mockk()
    transaction = mockk()

    every { user.uid } returns uid
    every { auth.currentUser } returns user
    every { firestore.collection("invitations") } returns collection
  }

  @Test
  fun createInvitation_persistsRoleFreeInvitationPayloadWithTrustedFieldsAndReturnsCode() =
      runBlocking {
        val invitationPayload = slot<Map<String, Any?>>()
        stubSuccessfulTransaction(document, invitationPayload)

        val result = repository().createInvitation(circleId)

        assertEquals(InvitationCreationResult.Created("CODE1234"), result)
        assertEquals(
            mapOf(
                "code" to "CODE1234",
                "circleId" to circleId,
                "createdBy" to uid,
                "createdAt" to createdAt,
                "expiresAt" to createdAt + thirtyDaysMillis,
                "usedBy" to null,
            ),
            invitationPayload.captured,
        )
        assertTrue("role" !in invitationPayload.captured)
      }

  @Test
  fun createInvitation_signedOutUserDoesNotAccessFirestore() = runBlocking {
    every { auth.currentUser } returns null

    val result = repository().createInvitation(circleId)

    assertEquals(InvitationCreationResult.NotAuthenticated, result)
    verify(exactly = 0) { firestore.collection(any()) }
  }

  @Test
  fun createInvitation_retriesWithNextCodeWhenTransactionFindsExistingInvitation() = runBlocking {
    val collidingDocument = mockk<DocumentReference>()
    val createdDocument = mockk<DocumentReference>()
    val existingSnapshot = mockk<DocumentSnapshot>()
    val missingSnapshot = mockk<DocumentSnapshot>()
    every { collection.document("COLLIDE1") } returns collidingDocument
    every { collection.document("SUCCESS2") } returns createdDocument
    every { existingSnapshot.exists() } returns true
    every { missingSnapshot.exists() } returns false
    every { transaction.get(collidingDocument) } returns existingSnapshot
    every { transaction.get(createdDocument) } returns missingSnapshot
    every { transaction.set(createdDocument, any()) } returns transaction
    every { firestore.runTransaction<Unit>(any()) } answers
        {
          firstArg<Transaction.Function<Unit>>().apply(transaction)
          Tasks.forResult(Unit)
        }
    val codes = ArrayDeque(listOf("COLLIDE1", "SUCCESS2"))

    val result = repository(codeGenerator = { codes.removeFirst() }).createInvitation(circleId)

    assertEquals(InvitationCreationResult.Created("SUCCESS2"), result)
    verify(exactly = 0) { transaction.set(collidingDocument, any()) }
    verify(exactly = 1) { transaction.set(createdDocument, any()) }
  }

  @Test
  fun createInvitation_returnsCollisionLimitWhenEveryCandidateExists() = runBlocking {
    val firstDocument = mockk<DocumentReference>()
    val secondDocument = mockk<DocumentReference>()
    val existingSnapshot = mockk<DocumentSnapshot>()
    every { collection.document("COLLIDE1") } returns firstDocument
    every { collection.document("COLLIDE2") } returns secondDocument
    every { existingSnapshot.exists() } returns true
    every { transaction.get(firstDocument) } returns existingSnapshot
    every { transaction.get(secondDocument) } returns existingSnapshot
    every { firestore.runTransaction<Unit>(any()) } answers
        {
          firstArg<Transaction.Function<Unit>>().apply(transaction)
          Tasks.forResult(Unit)
        }
    val codes = ArrayDeque(listOf("COLLIDE1", "COLLIDE2"))

    val result =
        repository(codeGenerator = { codes.removeFirst() }, maximumCreateAttempts = 2)
            .createInvitation(circleId)

    assertEquals(InvitationCreationResult.CollisionRetryLimitExceeded, result)
    verify(exactly = 0) { transaction.set(any(), any()) }
  }

  @Test
  fun createInvitation_retriesAfterAlreadyExistsCommitFailure() = runBlocking {
    val firstDocument = mockk<DocumentReference>()
    val secondDocument = mockk<DocumentReference>()
    val missingSnapshot = mockk<DocumentSnapshot>()
    every { collection.document("COLLIDE1") } returns firstDocument
    every { collection.document("SUCCESS2") } returns secondDocument
    every { missingSnapshot.exists() } returns false
    every { transaction.get(any()) } returns missingSnapshot
    every { transaction.set(any(), any()) } returns transaction
    every { firestore.runTransaction<Unit>(any()) } returns
        Tasks.forException(
            FirebaseFirestoreException("collision", FirebaseFirestoreException.Code.ALREADY_EXISTS)
        ) andThen
        Tasks.forResult(Unit)
    val codes = ArrayDeque(listOf("COLLIDE1", "SUCCESS2"))

    val result = repository(codeGenerator = { codes.removeFirst() }).createInvitation(circleId)

    assertEquals(InvitationCreationResult.Created("SUCCESS2"), result)
  }

  @Test
  fun createInvitation_mapsPermissionDeniedFailure() = runBlocking {
    stubFailedTransaction(
        FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
    )

    assertEquals(InvitationCreationResult.PermissionDenied, repository().createInvitation(circleId))
  }

  @Test
  fun createInvitation_mapsUnauthenticatedFailure() = runBlocking {
    stubFailedTransaction(
        FirebaseFirestoreException("signed out", FirebaseFirestoreException.Code.UNAUTHENTICATED)
    )

    assertEquals(InvitationCreationResult.NotAuthenticated, repository().createInvitation(circleId))
  }

  @Test
  fun createInvitation_mapsUnavailableFailureToNetworkFailure() = runBlocking {
    stubFailedTransaction(
        FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)
    )

    assertEquals(InvitationCreationResult.NetworkFailure, repository().createInvitation(circleId))
  }

  @Test
  fun createInvitation_mapsDeadlineExceededFailureToNetworkFailure() = runBlocking {
    stubFailedTransaction(
        FirebaseFirestoreException("timed out", FirebaseFirestoreException.Code.DEADLINE_EXCEEDED)
    )

    assertEquals(InvitationCreationResult.NetworkFailure, repository().createInvitation(circleId))
  }

  @Test
  fun createInvitation_keepsUnexpectedFirestoreFailureAsSource() = runBlocking {
    val source = FirebaseFirestoreException("internal", FirebaseFirestoreException.Code.INTERNAL)
    stubFailedTransaction(source)

    val result = repository().createInvitation(circleId)

    assertTrue(result is InvitationCreationResult.FirestoreFailure)
    assertSame(source, (result as InvitationCreationResult.FirestoreFailure).source)
  }

  @Test
  fun createInvitation_keepsNonFirestoreFailureAsSource() = runBlocking {
    val source = IllegalStateException("unexpected")
    stubFailedTransaction(source)

    val result = repository().createInvitation(circleId)

    assertTrue(result is InvitationCreationResult.FirestoreFailure)
    assertSame(source, (result as InvitationCreationResult.FirestoreFailure).source)
  }

  @Test
  fun createInvitation_rethrowsCancellation() {
    every { collection.document("CODE1234") } returns document
    every { firestore.runTransaction<Unit>(any()) } returns Tasks.forCanceled<Unit>()

    assertThrows(CancellationException::class.java) {
      runBlocking { repository().createInvitation(circleId) }
    }
  }

  @Test
  fun createInvitation_rejectsNonPositiveMaximumAttempts() {
    assertThrows(IllegalArgumentException::class.java) { repository(maximumCreateAttempts = 0) }
  }

  @Test
  fun createInvitation_doesNotWriteWhenExpiryOverflows() = runBlocking {
    val result = repository(timeProvider = { Long.MAX_VALUE }).createInvitation(circleId)

    assertEquals(InvitationCreationResult.InvalidExpiry, result)
    verify(exactly = 0) { firestore.collection(any()) }
  }

  private fun stubSuccessfulTransaction(
      reference: DocumentReference,
      invitationPayload: io.mockk.CapturingSlot<Map<String, Any?>>,
  ) {
    val missingSnapshot = mockk<DocumentSnapshot>()
    every { collection.document("CODE1234") } returns reference
    every { missingSnapshot.exists() } returns false
    every { transaction.get(reference) } returns missingSnapshot
    every { transaction.set(reference, capture(invitationPayload)) } returns transaction
    every { firestore.runTransaction<Unit>(any()) } answers
        {
          firstArg<Transaction.Function<Unit>>().apply(transaction)
          Tasks.forResult(Unit)
        }
  }

  private fun stubFailedTransaction(error: Exception) {
    every { collection.document("CODE1234") } returns document
    every { firestore.runTransaction<Unit>(any()) } returns Tasks.forException(error)
  }

  private fun repository(
      timeProvider: () -> Long = { createdAt },
      codeGenerator: () -> String = { "CODE1234" },
      maximumCreateAttempts: Int = 5,
  ) =
      FirebaseInvitationCreationRepository(
          auth = auth,
          firestore = firestore,
          timeProvider = timeProvider,
          codeGenerator = codeGenerator,
          maximumCreateAttempts = maximumCreateAttempts,
      )

  private companion object {
    const val thirtyDaysMillis = 30L * 24 * 60 * 60 * 1_000
  }
}
