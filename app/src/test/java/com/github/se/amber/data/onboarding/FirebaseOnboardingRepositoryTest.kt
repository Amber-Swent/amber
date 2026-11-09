// This file was written by Neha Chakraborty and Copilot
// Modified by Alaabrine, with assistance from Cursor (Grok).

package com.github.se.amber.data.onboarding

import com.github.se.amber.model.circle.CareCircle
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
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FirebaseOnboardingRepositoryTest {
  private val uid = "caregiver-123"
  private val circleId = "circle-456"
  private val createdAt = 1_700_000_000_000L

  private lateinit var auth: FirebaseAuth
  private lateinit var firestore: FirebaseFirestore
  private lateinit var users: CollectionReference
  private lateinit var circles: CollectionReference
  private lateinit var profileDocument: DocumentReference
  private lateinit var circleDocument: DocumentReference
  private lateinit var user: FirebaseUser
  private lateinit var transaction: Transaction
  private lateinit var repository: FirebaseOnboardingRepository

  @Before
  fun setUp() {
    auth = mockk()
    firestore = mockk()
    users = mockk()
    circles = mockk()
    profileDocument = mockk()
    circleDocument = mockk()
    user = mockk()
    transaction = mockk()

    every { user.uid } returns uid
    every { auth.currentUser } returns user
    every { firestore.collection("users") } returns users
    every { firestore.collection("careCircles") } returns circles
    every { users.document(uid) } returns profileDocument
    every { circles.document() } returns circleDocument
    every { circleDocument.id } returns circleId

    repository = FirebaseOnboardingRepository(auth, firestore) { createdAt }
  }

  @Test
  fun createInitialCareCircle_rejectsMissingProfileWithoutWriting() = runBlocking {
    stubTransactionWithProfile(profile = null, exists = false)

    val result = repository.createInitialCareCircle("Family")

    assertFailure<OnboardingError.ProfileMissing>(result)
    verify(exactly = 0) { transaction.set(circleDocument, any<CareCircle>()) }
    verify(exactly = 0) { transaction.update(profileDocument, any<String>(), any<Any>()) }
  }

  @Test
  fun createInitialCareCircle_rejectsBlankNameBeforeAccessingFirebase() = runBlocking {
    val result = repository.createInitialCareCircle("   ")

    assertFailure<OnboardingError.InvalidCircleName>(result)
    verify(exactly = 0) { firestore.collection(any()) }
  }

  @Test
  fun createInitialCareCircle_rejectsSignedOutUserBeforeAccessingFirestore() = runBlocking {
    every { auth.currentUser } returns null

    val result = repository.createInitialCareCircle("Family")

    assertFailure<OnboardingError.NotAuthenticated>(result)
    verify(exactly = 0) { firestore.collection(any()) }
  }

  @Test
  fun createInitialCareCircle_rejectsPatientProfileWithoutWriting() = runBlocking {
    stubTransactionWithProfile(profile = profile(role = Role.PATIENT))

    val result = repository.createInitialCareCircle("Family")

    assertFailure<OnboardingError.NotCaregiver>(result)
    verify(exactly = 0) { transaction.set(circleDocument, any<CareCircle>()) }
    verify(exactly = 0) { transaction.update(profileDocument, any<String>(), any<Any>()) }
  }

  @Test
  fun createInitialCareCircle_rejectsMalformedProfileWithoutWriting() = runBlocking {
    stubTransactionWithProfile(profile = profile(), validFields = setOf("uid", "role", "person"))

    val result = repository.createInitialCareCircle("Family")

    assertFailure<OnboardingError.InvalidProfile>(result)
    verify(exactly = 0) { transaction.set(circleDocument, any<CareCircle>()) }
    verify(exactly = 0) { transaction.update(profileDocument, any<String>(), any<Any>()) }
  }

  @Test
  fun createInitialCareCircle_rejectsPatientWhoAlreadyHasCirclesWithoutWriting() = runBlocking {
    stubTransactionWithProfile(
        profile =
            profile(
                role = Role.PATIENT,
                circleIds = listOf("existing-circle", "another-circle"),
            ),
    )

    val result = repository.createInitialCareCircle("Family")

    assertFailure<OnboardingError.NotCaregiver>(result)
    verify(exactly = 0) { transaction.set(circleDocument, any<CareCircle>()) }
    verify(exactly = 0) { transaction.update(profileDocument, any<String>(), any<Any>()) }
  }

  @Test
  fun createInitialCareCircle_createsAndLinksCircleWithinTransaction() = runBlocking {
    val write = stubSuccessfulTransaction(profile())

    val result = repository.createInitialCareCircle("  Family  ")

    assertCircleCreatedAndLinked(result, name = "Family", write = write)
  }

  @Test
  fun createInitialCareCircle_createsSecondCircleAndArrayUnionsItsId() = runBlocking {
    val write = stubSuccessfulTransaction(profile(circleIds = listOf("existing-circle")))

    val result = repository.createInitialCareCircle("Parents")

    assertCircleCreatedAndLinked(result, name = "Parents", write = write)
  }

  @Test
  fun createInitialCareCircle_createsCircleWhenCaregiverAlreadyHasSeveral() = runBlocking {
    val write =
        stubSuccessfulTransaction(
            profile(circleIds = listOf("circle-a", "circle-b", "circle-c")),
        )

    val result = repository.createInitialCareCircle("  Neighbors  ")

    assertCircleCreatedAndLinked(result, name = "Neighbors", write = write)
  }

  @Test
  fun createInitialCareCircle_arrayUnionsEachNewIdWhenCreatingMultipleCircles() = runBlocking {
    val firstNewCircle = mockk<DocumentReference>()
    val secondNewCircle = mockk<DocumentReference>()
    every { firstNewCircle.id } returns "new-circle-1"
    every { secondNewCircle.id } returns "new-circle-2"
    every { circles.document() } returnsMany listOf(firstNewCircle, secondNewCircle)

    val createdCircles = mutableListOf<CareCircle>()
    val updatedFields = mutableListOf<String>()
    val updatedValues = mutableListOf<Any>()
    every { transaction.get(profileDocument) } returns
        profileSnapshot(profile(circleIds = listOf("existing-circle")))
    every { transaction.set(any<DocumentReference>(), any<CareCircle>()) } answers
        {
          createdCircles += secondArg<CareCircle>()
          transaction
        }
    every { transaction.update(profileDocument, any<String>(), any()) } answers
        {
          updatedFields += secondArg<String>()
          updatedValues += thirdArg<Any>()
          transaction
        }
    every { firestore.runTransaction<CareCircle>(any()) } answers
        {
          val circle = firstArg<Transaction.Function<CareCircle>>().apply(transaction)
          Tasks.forResult(circle)
        }

    val firstResult = repository.createInitialCareCircle("Family")
    val secondResult = repository.createInitialCareCircle("Friends")

    assertEquals(
        CareCircle(
            id = "new-circle-1",
            name = "Family",
            memberIds = listOf(uid),
            createdBy = uid,
            createdAt = createdAt,
        ),
        firstResult.getOrThrow(),
    )
    assertEquals(
        CareCircle(
            id = "new-circle-2",
            name = "Friends",
            memberIds = listOf(uid),
            createdBy = uid,
            createdAt = createdAt,
        ),
        secondResult.getOrThrow(),
    )
    assertEquals(listOf(firstResult.getOrThrow(), secondResult.getOrThrow()), createdCircles)
    assertEquals(listOf("circleIds", "circleIds"), updatedFields)
    assertIsArrayUnionOf(updatedValues[0], "new-circle-1")
    assertIsArrayUnionOf(updatedValues[1], "new-circle-2")
    verify(exactly = 1) { transaction.set(firstNewCircle, any<CareCircle>()) }
    verify(exactly = 1) { transaction.set(secondNewCircle, any<CareCircle>()) }
  }

  @Test
  fun createInitialCareCircle_mapsPermissionDeniedFirestoreFailure() = runBlocking {
    stubFailedTransaction(
        FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
    )

    val result = repository.createInitialCareCircle("Family")

    assertFailure<OnboardingError.PermissionDenied>(result)
  }

  @Test
  fun createInitialCareCircle_mapsUnauthenticatedFirestoreFailure() = runBlocking {
    stubFailedTransaction(
        FirebaseFirestoreException("signed out", FirebaseFirestoreException.Code.UNAUTHENTICATED)
    )

    val result = repository.createInitialCareCircle("Family")

    assertFailure<OnboardingError.NotAuthenticated>(result)
  }

  @Test
  fun createInitialCareCircle_mapsUnavailableFirestoreFailureToNetwork() = runBlocking {
    stubFailedTransaction(
        FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)
    )

    val result = repository.createInitialCareCircle("Family")

    assertFailure<OnboardingError.Network>(result)
  }

  @Test
  fun createInitialCareCircle_mapsDeadlineExceededFirestoreFailureToNetwork() = runBlocking {
    stubFailedTransaction(
        FirebaseFirestoreException("timed out", FirebaseFirestoreException.Code.DEADLINE_EXCEEDED)
    )

    val result = repository.createInitialCareCircle("Family")

    assertFailure<OnboardingError.Network>(result)
  }

  @Test
  fun createInitialCareCircle_wrapsUnexpectedFirestoreFailure() = runBlocking {
    val source = FirebaseFirestoreException("failed", FirebaseFirestoreException.Code.INTERNAL)
    stubFailedTransaction(source)

    val result = repository.createInitialCareCircle("Family")

    assertUnknownFailure(result, source)
  }

  @Test
  fun createInitialCareCircle_wrapsUnexpectedFailure() = runBlocking {
    val source = IllegalStateException("failed")
    stubFailedTransaction(source)

    val result = repository.createInitialCareCircle("Family")

    assertUnknownFailure(result, source)
  }

  private class CircleTransaction(
      val createdCircle: CapturingSlot<CareCircle> = slot(),
      val updatedField: CapturingSlot<String> = slot(),
      val updatedValue: CapturingSlot<Any> = slot(),
  )

  private fun stubSuccessfulTransaction(currentProfile: UserProfile): CircleTransaction {
    val write = CircleTransaction()
    every { transaction.get(profileDocument) } returns profileSnapshot(currentProfile)
    every { transaction.set(circleDocument, capture(write.createdCircle)) } returns transaction
    every {
      transaction.update(
          profileDocument,
          capture(write.updatedField),
          capture(write.updatedValue),
      )
    } returns transaction
    every { firestore.runTransaction<CareCircle>(any()) } answers
        {
          val circle = firstArg<Transaction.Function<CareCircle>>().apply(transaction)
          Tasks.forResult(circle)
        }
    return write
  }

  private fun assertCircleCreatedAndLinked(
      result: Result<CareCircle>,
      name: String,
      write: CircleTransaction,
  ) {
    val created =
        CareCircle(
            id = circleId,
            name = name,
            memberIds = listOf(uid),
            createdBy = uid,
            createdAt = createdAt,
        )
    assertEquals(created, result.getOrThrow())
    assertEquals(created, write.createdCircle.captured)
    assertEquals("circleIds", write.updatedField.captured)
    assertIsArrayUnionOf(write.updatedValue.captured, circleId)
    verify(exactly = 1) { transaction.set(circleDocument, any<CareCircle>()) }
    verify(exactly = 1) { transaction.update(profileDocument, any<String>(), any<Any>()) }
  }

  private fun assertIsArrayUnionOf(value: Any, element: String) {
    assertEquals("ArrayUnionFieldValue", value.javaClass.simpleName)
    val elements =
        value.javaClass.getDeclaredMethod("getElements").apply { isAccessible = true }.invoke(value)
            as List<*>
    assertEquals(listOf(element), elements)
  }

  private fun stubTransactionWithProfile(
      profile: UserProfile?,
      exists: Boolean = true,
      validFields: Set<String> = setOf("uid", "role", "person", "circleIds"),
  ) {
    val snapshot = profileSnapshot(profile, exists, validFields)
    every { transaction.get(profileDocument) } returns snapshot
    every { firestore.runTransaction<CareCircle>(any()) } answers
        {
          firstArg<Transaction.Function<CareCircle>>().apply(transaction)
          error("The transaction should fail before committing")
        }
  }

  private fun stubFailedTransaction(error: Exception) {
    every { firestore.runTransaction<CareCircle>(any()) } returns Tasks.forException(error)
  }

  private fun profileSnapshot(
      profile: UserProfile?,
      exists: Boolean = true,
      validFields: Set<String> = setOf("uid", "role", "person", "circleIds"),
  ): DocumentSnapshot = mockk {
    every { this@mockk.exists() } returns exists
    every { toObject(UserProfile::class.java) } returns profile
    every { contains(any<String>()) } answers { firstArg<String>() in validFields }
  }

  private fun profile(
      role: Role = Role.CAREGIVER,
      circleIds: List<String> = emptyList(),
  ) =
      UserProfile(
          uid = uid,
          role = role,
          person = Person(id = uid, firstName = "Ada"),
          circleIds = circleIds,
      )

  private inline fun <reified T : Throwable> assertFailure(result: Result<CareCircle>) {
    assertFalse(result.isSuccess)
    assertTrue(result.exceptionOrNull() is T)
  }

  private fun assertUnknownFailure(result: Result<CareCircle>, source: Throwable) {
    assertFalse(result.isSuccess)
    val error = result.exceptionOrNull()
    assertTrue(error is OnboardingError.Unknown)
    assertSame(source, (error as OnboardingError.Unknown).source)
  }
}
