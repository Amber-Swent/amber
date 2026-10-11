// This file was written by Neha Chakraborty and Copilot
package com.github.se.amber.data.profile

import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FirebaseProfileRepositoryTest {
  private val uid = "user-123"

  private lateinit var auth: FirebaseAuth
  private lateinit var firestore: FirebaseFirestore
  private lateinit var collection: CollectionReference
  private lateinit var document: DocumentReference
  private lateinit var user: FirebaseUser
  private lateinit var transaction: Transaction
  private lateinit var repository: FirebaseProfileRepository

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
    every { firestore.collection("users") } returns collection
    every { collection.document(uid) } returns document

    repository = FirebaseProfileRepository(auth, firestore)
  }

  @Test
  fun createProfile_createsPatientProfileForAuthenticatedUser() = runBlocking {
    val capturedProfile = stubSuccessfulTransaction()

    val result =
        repository.createProfile(
            Person(id = "untrusted-id", firstName = "Ada", lastName = "Lovelace", nickname = "Ada"),
            Role.PATIENT,
        )

    assertTrue(result.isSuccess)
    assertEquals(
        UserProfile(
            uid = uid,
            role = Role.PATIENT,
            person = Person(id = uid, firstName = "Ada", lastName = "Lovelace", nickname = "Ada"),
            circleIds = emptyList(),
        ),
        capturedProfile.captured,
    )
  }

  @Test
  fun createProfile_createsCaregiverProfile() = runBlocking {
    val capturedProfile = stubSuccessfulTransaction()

    val result = repository.createProfile(Person(firstName = "Grace"), Role.CAREGIVER)

    assertTrue(result.isSuccess)
    assertEquals(Role.CAREGIVER, capturedProfile.captured.role)
    assertEquals(uid, capturedProfile.captured.uid)
    assertEquals(uid, capturedProfile.captured.person.id)
    assertTrue(capturedProfile.captured.circleIds.isEmpty())
  }

  @Test
  fun createProfile_rejectsBlankFirstNameBeforeAccessingFirebase() = runBlocking {
    val result = repository.createProfile(Person(firstName = "   "), Role.PATIENT)

    assertFailure<ProfileCreationError.InvalidPersonDetails>(result)
    verify(exactly = 0) { firestore.collection(any()) }
  }

  @Test
  fun createProfile_rejectsSignedOutUserBeforeAccessingFirestore() = runBlocking {
    every { auth.currentUser } returns null

    val result = repository.createProfile(Person(firstName = "Ada"), Role.PATIENT)

    assertFailure<ProfileCreationError.NotAuthenticated>(result)
    verify(exactly = 0) { firestore.collection(any()) }
  }

  @Test
  fun createProfile_rejectsExistingProfileWithoutWriting() = runBlocking {
    val snapshot = mockk<DocumentSnapshot>()
    every { snapshot.exists() } returns true
    every { transaction.get(document) } returns snapshot
    every { firestore.runTransaction<Unit>(any()) } answers
        {
          firstArg<Transaction.Function<Unit>>().apply(transaction)
          Tasks.forResult(Unit)
        }

    val result = repository.createProfile(Person(firstName = "Ada"), Role.PATIENT)

    assertFailure<ProfileCreationError.ProfileAlreadyExists>(result)
    verify(exactly = 0) { transaction.set(any(), any()) }
  }

  @Test
  fun createProfile_rethrowsCancellationException() {
    val cancellation = CancellationException("profile creation cancelled")
    stubFailedTransaction(cancellation)

    val thrown =
        assertThrows(CancellationException::class.java) {
          runBlocking { repository.createProfile(Person(firstName = "Ada"), Role.PATIENT) }
        }

    assertSame(cancellation, thrown)
  }

  @Test
  fun createProfile_mapsPermissionDeniedFirestoreFailure() = runBlocking {
    stubFailedTransaction(
        FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
    )

    val result = repository.createProfile(Person(firstName = "Ada"), Role.PATIENT)

    assertFailure<ProfileCreationError.PermissionDenied>(result)
  }

  @Test
  fun createProfile_mapsUnauthenticatedFirestoreFailure() = runBlocking {
    stubFailedTransaction(
        FirebaseFirestoreException("signed out", FirebaseFirestoreException.Code.UNAUTHENTICATED)
    )

    val result = repository.createProfile(Person(firstName = "Ada"), Role.PATIENT)

    assertFailure<ProfileCreationError.NotAuthenticated>(result)
  }

  @Test
  fun createProfile_mapsUnavailableFirestoreFailureToNetwork() = runBlocking {
    stubFailedTransaction(
        FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)
    )

    val result = repository.createProfile(Person(firstName = "Ada"), Role.PATIENT)

    assertFailure<ProfileCreationError.Network>(result)
  }

  @Test
  fun createProfile_mapsDeadlineExceededFirestoreFailureToNetwork() = runBlocking {
    stubFailedTransaction(
        FirebaseFirestoreException("timed out", FirebaseFirestoreException.Code.DEADLINE_EXCEEDED)
    )

    val result = repository.createProfile(Person(firstName = "Ada"), Role.PATIENT)

    assertFailure<ProfileCreationError.Network>(result)
  }

  @Test
  fun createProfile_mapsOtherFirestoreFailureToUnknown() = runBlocking {
    val source = FirebaseFirestoreException("failed", FirebaseFirestoreException.Code.INTERNAL)
    stubFailedTransaction(source)

    val result = repository.createProfile(Person(firstName = "Ada"), Role.PATIENT)

    assertFalse(result.isSuccess)
    val error = result.exceptionOrNull()
    assertTrue(error is ProfileCreationError.Unknown)
    assertSame(source, (error as ProfileCreationError.Unknown).source)
  }

  private fun stubSuccessfulTransaction() =
      slot<UserProfile>().also { capturedProfile ->
        val snapshot = mockk<DocumentSnapshot>()
        every { snapshot.exists() } returns false
        every { transaction.get(document) } returns snapshot
        every { transaction.set(document, capture(capturedProfile)) } returns transaction
        every { firestore.runTransaction<Unit>(any()) } answers
            {
              firstArg<Transaction.Function<Unit>>().apply(transaction)
              Tasks.forResult(Unit)
            }
      }

  private fun stubFailedTransaction(error: Exception) {
    every { firestore.runTransaction<Unit>(any()) } returns Tasks.forException(error)
  }

  private inline fun <reified T : Throwable> assertFailure(result: Result<Unit>) {
    assertFalse(result.isSuccess)
    assertTrue(result.exceptionOrNull() is T)
  }
}
