// This file was written by Neha Chakraborty and Copilot
package com.github.se.amber.data.invitation

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class FirebaseInvitationLookupRepositoryTest {
  private val uid = "caregiver-123"
  private val circleId = "circle-456"
  private val now = 1_700_000_000_000L

  private lateinit var auth: FirebaseAuth
  private lateinit var firestore: FirebaseFirestore
  private lateinit var collection: CollectionReference
  private lateinit var creatorQuery: Query
  private lateinit var circleQuery: Query
  private lateinit var user: FirebaseUser

  @Before
  fun setUp() {
    auth = mockk()
    firestore = mockk()
    collection = mockk()
    creatorQuery = mockk()
    circleQuery = mockk()
    user = mockk()

    every { user.uid } returns uid
    every { auth.currentUser } returns user
    every { firestore.collection("invitations") } returns collection
    every { collection.whereEqualTo("createdBy", uid) } returns creatorQuery
    every { creatorQuery.whereEqualTo("circleId", circleId) } returns circleQuery
  }

  @Test
  fun getInvitations_queriesCurrentCaregiverAndCircleAndOrdersNewestFirst() = runBlocking {
    stubInvitations(
        invitation(code = "OLDER", createdAt = now - 2_000, expiresAt = now + 1_000),
        invitation(code = "NEWER", createdAt = now - 1_000, expiresAt = now + 1_000),
    )

    val result = repository().getInvitations(circleId)

    assertEquals(
        listOf(
            InvitationSummary("NEWER", now - 1_000, now + 1_000, InvitationStatus.ACTIVE),
            InvitationSummary("OLDER", now - 2_000, now + 1_000, InvitationStatus.ACTIVE),
        ),
        result.getOrThrow(),
    )
    verify(exactly = 1) { collection.whereEqualTo("createdBy", uid) }
    verify(exactly = 1) { creatorQuery.whereEqualTo("circleId", circleId) }
  }

  @Test
  fun getInvitations_classifiesActiveExpiredAndUsedInvitations() = runBlocking {
    stubInvitations(
        invitation(code = "ACTIVE", createdAt = now - 3_000, expiresAt = now + 1_000),
        invitation(code = "EXPIRED", createdAt = now - 2_000, expiresAt = now - 1),
        invitation(
            code = "USED",
            createdAt = now - 1_000,
            expiresAt = now + 1_000,
            usedBy = "user-9",
        ),
    )

    val invitations = repository().getInvitations(circleId).getOrThrow()

    assertEquals(
        listOf(
            InvitationStatus.USED,
            InvitationStatus.EXPIRED,
            InvitationStatus.ACTIVE,
        ),
        invitations.map(InvitationSummary::status),
    )
  }

  @Test
  fun getInvitations_returnsEmptyListForCircleWithoutInvitations() = runBlocking {
    stubInvitations()

    val result = repository().getInvitations(circleId)

    assertEquals(Result.success(emptyList<InvitationSummary>()), result)
  }

  @Test
  fun getInvitations_treatsExpiryBoundaryAsExpired() = runBlocking {
    stubInvitations(invitation(code = "EXPIRES_NOW", createdAt = now - 1, expiresAt = now))

    val invitation = repository().getInvitations(circleId).getOrThrow().single()

    assertEquals(InvitationStatus.EXPIRED, invitation.status)
  }

  @Test
  fun getInvitations_keepsUsedStatusWhenUsedInvitationHasExpired() = runBlocking {
    stubInvitations(
        invitation(
            code = "USED_AND_EXPIRED",
            createdAt = now - 1,
            expiresAt = now - 1,
            usedBy = "user-9",
        ),
    )

    val invitation = repository().getInvitations(circleId).getOrThrow().single()

    assertEquals(InvitationStatus.USED, invitation.status)
  }

  @Test
  fun getInvitations_failsWholeLookupForInvitationWithoutCreationTime() = runBlocking {
    stubInvitations(
        invitation(code = "VALID", createdAt = now - 2, expiresAt = now + 1_000),
        invitation(code = "MISSING_CREATED", createdAt = null, expiresAt = now + 1_000),
    )

    val result = repository().getInvitations(circleId)

    assertNull(result.getOrNull())
    assertEquals(
        "Invitation MISSING_CREATED has an invalid format",
        result.exceptionOrNull()?.message,
    )
  }

  @Test
  fun getInvitations_failsWholeLookupForInvitationWithoutExpiryTime() = runBlocking {
    stubInvitations(
        invitation(code = "VALID", createdAt = now - 2, expiresAt = now + 1_000),
        invitation(code = "MISSING_EXPIRY", createdAt = now - 1, expiresAt = null),
    )

    val result = repository().getInvitations(circleId)

    assertNull(result.getOrNull())
    assertEquals(
        "Invitation MISSING_EXPIRY has an invalid format",
        result.exceptionOrNull()?.message,
    )
  }

  @Test
  fun getInvitations_failsWholeLookupForInvitationWithInvalidUsedByType() = runBlocking {
    stubInvitations(
        invitation(code = "VALID", createdAt = now - 2, expiresAt = now + 1_000),
        invitation(code = "INVALID_USED_BY", createdAt = now - 1, expiresAt = now + 1, usedBy = 42),
    )

    val result = repository().getInvitations(circleId)

    assertNull(result.getOrNull())
    assertEquals(
        "Invitation INVALID_USED_BY has an invalid format",
        result.exceptionOrNull()?.message,
    )
  }

  @Test
  fun getInvitations_signedOutUserDoesNotAccessFirestore() = runBlocking {
    every { auth.currentUser } returns null

    val error = repository().getInvitations(circleId).exceptionOrNull()

    assertEquals("No Firebase user is signed in", error?.message)
    verify(exactly = 0) { firestore.collection(any()) }
  }

  @Test
  fun getInvitations_propagatesFirestoreFailure() = runBlocking {
    val source = IllegalStateException("Firestore unavailable")
    every { circleQuery.get() } returns Tasks.forException(source)

    val error = repository().getInvitations(circleId).exceptionOrNull()

    assertSame(source, error)
  }

  @Test
  fun getInvitations_rethrowsCancellation() {
    every { circleQuery.get() } returns Tasks.forCanceled()

    assertThrows(CancellationException::class.java) {
      runBlocking { repository().getInvitations(circleId) }
    }
  }

  private fun stubInvitations(vararg documents: DocumentSnapshot) {
    val snapshot = mockk<QuerySnapshot>()
    every { snapshot.documents } returns documents.toList()
    every { circleQuery.get() } returns Tasks.forResult(snapshot)
  }

  private fun invitation(
      code: String,
      createdAt: Long?,
      expiresAt: Long?,
      usedBy: Any? = null,
  ): DocumentSnapshot {
    val snapshot = mockk<DocumentSnapshot>()
    every { snapshot.id } returns code
    every { snapshot.getLong("createdAt") } returns createdAt
    every { snapshot.getLong("expiresAt") } returns expiresAt
    every { snapshot.get("usedBy") } returns usedBy
    return snapshot
  }

  private fun repository() =
      FirebaseInvitationLookupRepository(
          auth = auth,
          firestore = firestore,
          timeProvider = { now },
      )
}
