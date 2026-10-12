// This file was written by Neha Chakraborty and Copilot
package com.github.se.amber.data.invitation

import com.github.se.amber.model.user.Role
import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

class FirebaseInvitationRedemptionRepositoryTest {
  private lateinit var functions: FirebaseFunctions
  private lateinit var callable: HttpsCallableReference
  private lateinit var repository: FirebaseInvitationRedemptionRepository

  @Before
  fun setUp() {
    functions = mock(FirebaseFunctions::class.java)
    callable = mock(HttpsCallableReference::class.java)
    `when`(functions.getHttpsCallable("redeemInvitation")).thenReturn(callable)
    repository = FirebaseInvitationRedemptionRepository(functions)
  }

  @Test
  fun redeemInvitationSendsCodePayloadAndParsesSuccessfulResponse() = runBlocking {
    respondWith(mapOf("circleId" to "circle-1", "role" to "CAREGIVER"))

    val result = repository.redeemInvitation("valid-code_1")

    assertEquals(
        InvitationRedemptionResult(careCircleId = "circle-1", role = Role.CAREGIVER),
        result.getOrThrow(),
    )
    verify(functions).getHttpsCallable("redeemInvitation")
    verify(callable).call(eq(mapOf("code" to "valid-code_1")))
    Unit
  }

  @Test
  fun redeemInvitationRejectsMalformedResponse() = runBlocking {
    respondWith(mapOf("circleId" to "circle-1", "role" to "UNKNOWN"))

    val error = repository.redeemInvitation("valid-code").exceptionOrNull()

    val unknown = error as InvitationError.Unknown
    assertEquals("redeemInvitation returned an invalid response", unknown.source.message)
  }

  @Test
  fun redeemInvitationRejectsInvalidLocalCodeWithoutCallingFirebase() = runBlocking {
    val result = repository.redeemInvitation("invalid code!")

    assertSame(InvitationError.InvalidCode, result.exceptionOrNull())
    verifyNoInteractions(functions, callable)
  }

  @Test
  fun redeemInvitationMapsInvalidArgumentToInvalidCode() = runBlocking {
    respondWithFailure(FirebaseFunctionsException.Code.INVALID_ARGUMENT, "Invalid code")

    assertSame(
        InvitationError.InvalidCode,
        repository.redeemInvitation("valid-code").exceptionOrNull(),
    )
  }

  @Test
  fun redeemInvitationMapsNotFoundToInvitationNotFound() = runBlocking {
    respondWithFailure(FirebaseFunctionsException.Code.NOT_FOUND, "Invitation not found")

    assertSame(
        InvitationError.InvitationNotFound,
        repository.redeemInvitation("valid-code").exceptionOrNull(),
    )
  }

  @Test
  fun redeemInvitationMapsUnavailableToNetwork() = runBlocking {
    respondWithFailure(FirebaseFunctionsException.Code.UNAVAILABLE, "Service unavailable")

    assertSame(InvitationError.Network, repository.redeemInvitation("valid-code").exceptionOrNull())
  }

  @Test
  fun redeemInvitationMapsDeadlineExceededToNetwork() = runBlocking {
    respondWithFailure(FirebaseFunctionsException.Code.DEADLINE_EXCEEDED, "Request timed out")

    assertSame(InvitationError.Network, repository.redeemInvitation("valid-code").exceptionOrNull())
  }

  @Test
  fun redeemInvitationMapsAlreadyRedeemedPrecondition() = runBlocking {
    assertPreconditionMaps(
        "Invitation has already been redeemed",
        InvitationError.InvitationAlreadyUsed,
    )
  }

  @Test
  fun redeemInvitationMapsExpiredPrecondition() = runBlocking {
    assertPreconditionMaps("Invitation has expired", InvitationError.InvitationExpired)
  }

  @Test
  fun redeemInvitationMapsExistingMemberPrecondition() = runBlocking {
    assertPreconditionMaps(
        "User is already a circle member",
        InvitationError.UserAlreadyBelongsToCircle,
    )
  }

  @Test
  fun redeemInvitationMapsProfilePrecondition() = runBlocking {
    assertPreconditionMaps(
        "User profile is missing or invalid",
        InvitationError.MissingOrInvalidUserProfile,
    )
  }

  @Test
  fun redeemInvitationMapsPatientSlotPrecondition() = runBlocking {
    assertPreconditionMaps(
        "Care circle already has a patient",
        InvitationError.PatientSlotAlreadyOccupied,
    )
  }

  @Test
  fun redeemInvitationMapsCirclePrecondition() = runBlocking {
    assertPreconditionMaps(
        "Invitation references an unavailable circle",
        InvitationError.InvalidOrUnavailableCareCircle,
    )
  }

  @Test
  fun redeemInvitationMapsUnrecognizedCallableFailureToUnknown() = runBlocking {
    val failure = functionsException(FirebaseFunctionsException.Code.INTERNAL, "Internal")
    `when`(callable.call(any())).thenReturn(Tasks.forException(failure))

    val error = repository.redeemInvitation("valid-code").exceptionOrNull()

    assertEquals(InvitationError.Unknown(failure), error)
    assertSame(failure, (error as InvitationError.Unknown).source)
  }

  @Test
  fun redeemInvitationPropagatesCancellation() {
    `when`(callable.call(any())).thenReturn(Tasks.forCanceled<HttpsCallableResult>())

    assertThrows(CancellationException::class.java) {
      runBlocking { repository.redeemInvitation("valid-code") }
    }
  }

  private fun respondWith(data: Any?) {
    `when`(callable.call(any())).thenReturn(Tasks.forResult(callableResult(data)))
  }

  private fun callableResult(data: Any?): HttpsCallableResult =
      HttpsCallableResult::class.java.getConstructor(Any::class.java).newInstance(data)

  private fun respondWithFailure(code: FirebaseFunctionsException.Code, message: String) {
    `when`(callable.call(any())).thenReturn(Tasks.forException(functionsException(code, message)))
  }

  private fun functionsException(
      code: FirebaseFunctionsException.Code,
      message: String,
  ): FirebaseFunctionsException =
      FirebaseFunctionsException::class
          .java
          .getConstructor(
              String::class.java,
              FirebaseFunctionsException.Code::class.java,
              Any::class.java,
          )
          .newInstance(message, code, null)

  private suspend fun assertPreconditionMaps(message: String, expected: InvitationError) {
    respondWithFailure(FirebaseFunctionsException.Code.FAILED_PRECONDITION, message)

    assertSame(expected, repository.redeemInvitation("valid-code").exceptionOrNull())
  }
}
