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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
  fun createProfile_createsPatientProfileForAuthenticatedUser() = runTest {
    val capturedProfile = stubSuccessfulTransaction()

    val result =
        repository.createProfile(
            Person(
                id = "untrusted-id",
                firstName = " Ada ",
                lastName = " Lovelace ",
                nickname = "Ada",
            ),
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
  fun createProfile_createsCaregiverProfile() = runTest {
    val capturedProfile = stubSuccessfulTransaction()

    val result = repository.createProfile(Person(firstName = "Grace"), Role.CAREGIVER)

    assertTrue(result.isSuccess)
    assertEquals(Role.CAREGIVER, capturedProfile.captured.role)
    assertEquals(uid, capturedProfile.captured.uid)
    assertEquals(uid, capturedProfile.captured.person.id)
    assertTrue(capturedProfile.captured.circleIds.isEmpty())
  }

  @Test
  fun createProfile_rejectsBlankFirstNameBeforeAccessingFirebase() = runTest {
    val result = repository.createProfile(Person(firstName = "   "), Role.PATIENT)

    assertFailure<FirebaseProfileRepository.ProfileCreationError.InvalidPersonDetails>(result)
    verify(exactly = 0) { firestore.collection(any()) }
  }

  @Test
  fun createProfile_rejectsSignedOutUserBeforeAccessingFirestore() = runTest {
    every { auth.currentUser } returns null

    val result = repository.createProfile(Person(firstName = "Ada"), Role.PATIENT)

    assertFailure<FirebaseProfileRepository.ProfileCreationError.NotAuthenticated>(result)
    verify(exactly = 0) { firestore.collection(any()) }
  }

  @Test
  fun createProfile_rejectsExistingProfileWithoutWriting() = runTest {
    val snapshot = mockk<DocumentSnapshot>()
    every { snapshot.exists() } returns true
    every { transaction.get(document) } returns snapshot
    every { firestore.runTransaction<Unit>(any()) } answers
        {
          runTransactionFunction(firstArg(), transaction)
        }

    val result = repository.createProfile(Person(firstName = "Ada"), Role.PATIENT)

    assertFailure<FirebaseProfileRepository.ProfileCreationError.ProfileAlreadyExists>(result)
    verify(exactly = 0) { transaction.set(any(), any()) }
  }

  @Test
  fun createProfile_rethrowsCancellationException() = runTest {
    val cancellation = CancellationException("profile creation cancelled")
    stubFailedTransaction(cancellation)

    try {
      repository.createProfile(Person(firstName = "Ada"), Role.PATIENT)
      fail("Expected cancellation to be rethrown")
    } catch (thrown: CancellationException) {
      assertSame(cancellation, thrown)
    }
  }

  @Test
  fun createProfile_mapsPermissionDeniedFirestoreFailure() = runTest {
    assertFailure<FirebaseProfileRepository.ProfileCreationError.PermissionDenied>(
        createProfileFailingWith(
            firestoreFailure(FirebaseFirestoreException.Code.PERMISSION_DENIED)
        )
    )
  }

  @Test
  fun createProfile_mapsUnauthenticatedFirestoreFailure() = runTest {
    assertFailure<FirebaseProfileRepository.ProfileCreationError.NotAuthenticated>(
        createProfileFailingWith(firestoreFailure(FirebaseFirestoreException.Code.UNAUTHENTICATED))
    )
  }

  @Test
  fun createProfile_mapsUnavailableFirestoreFailureToNetwork() = runTest {
    assertFailure<FirebaseProfileRepository.ProfileCreationError.Network>(
        createProfileFailingWith(firestoreFailure(FirebaseFirestoreException.Code.UNAVAILABLE))
    )
  }

  @Test
  fun createProfile_mapsDeadlineExceededFirestoreFailureToNetwork() = runTest {
    assertFailure<FirebaseProfileRepository.ProfileCreationError.Network>(
        createProfileFailingWith(
            firestoreFailure(FirebaseFirestoreException.Code.DEADLINE_EXCEEDED)
        )
    )
  }

  @Test
  fun createProfile_mapsAbortedFirestoreFailureToNetwork() = runTest {
    assertFailure<FirebaseProfileRepository.ProfileCreationError.Network>(
        createProfileFailingWith(firestoreFailure(FirebaseFirestoreException.Code.ABORTED))
    )
  }

  @Test
  fun createProfile_mapsAlreadyExistsFirestoreFailure() = runTest {
    assertFailure<FirebaseProfileRepository.ProfileCreationError.ProfileAlreadyExists>(
        createProfileFailingWith(firestoreFailure(FirebaseFirestoreException.Code.ALREADY_EXISTS))
    )
  }

  @Test
  fun createProfile_mapsOtherFirestoreFailureToUnknown() = runTest {
    val source = firestoreFailure(FirebaseFirestoreException.Code.INTERNAL)
    val result = createProfileFailingWith(source)

    assertFalse(result.isSuccess)
    val error = result.exceptionOrNull()
    assertTrue(error is FirebaseProfileRepository.ProfileCreationError.Unknown)
    assertSame(source, (error as FirebaseProfileRepository.ProfileCreationError.Unknown).source)
  }

  @Test
  fun createProfile_mapsUnexpectedExceptionToUnknown() = runTest {
    val source = IllegalStateException("boom")
    val result = createProfileFailingWith(source)

    assertFalse(result.isSuccess)
    val error = result.exceptionOrNull()
    assertTrue(error is FirebaseProfileRepository.ProfileCreationError.Unknown)
    assertSame(source, (error as FirebaseProfileRepository.ProfileCreationError.Unknown).source)
  }

  private fun stubSuccessfulTransaction() =
      slot<UserProfile>().also { capturedProfile ->
        val snapshot = mockk<DocumentSnapshot>()
        every { snapshot.exists() } returns false
        every { transaction.get(document) } returns snapshot
        every { transaction.set(document, capture(capturedProfile)) } returns transaction
        every { firestore.runTransaction<Unit>(any()) } answers
            {
              runTransactionFunction(firstArg(), transaction)
            }
      }

  private fun runTransactionFunction(
      function: Transaction.Function<Unit>,
      transaction: Transaction,
  ) =
      try {
        function.apply(transaction)
        Tasks.forResult(Unit)
      } catch (error: Exception) {
        Tasks.forException(error)
      }

  private fun stubFailedTransaction(error: Exception) {
    every { firestore.runTransaction<Unit>(any()) } returns Tasks.forException(error)
  }

  private fun firestoreFailure(code: FirebaseFirestoreException.Code) =
      FirebaseFirestoreException("error", code)

  private suspend fun createProfileFailingWith(error: Exception): Result<Unit> {
    stubFailedTransaction(error)
    return repository.createProfile(Person(firstName = "Ada"), Role.PATIENT)
  }

  private inline fun <reified T : Throwable> assertFailure(result: Result<Unit>) {
    assertFalse(result.isSuccess)
    assertTrue(result.exceptionOrNull() is T)
  }
}
