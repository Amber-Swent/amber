// This file was written by Neha Chakraborty and Copilot, with assistance from
// Claude (Anthropic) via Claude Code.
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
    assertPreconditionMaps("INVITATION_ALREADY_REDEEMED", InvitationError.InvitationAlreadyUsed)
  }

  @Test
  fun redeemInvitationMapsExpiredPrecondition() = runBlocking {
    assertPreconditionMaps("INVITATION_EXPIRED", InvitationError.InvitationExpired)
  }

  @Test
  fun redeemInvitationMapsExistingMemberPrecondition() = runBlocking {
    assertPreconditionMaps("ALREADY_CIRCLE_MEMBER", InvitationError.UserAlreadyBelongsToCircle)
  }

  @Test
  fun redeemInvitationMapsProfilePrecondition() = runBlocking {
    assertPreconditionMaps("PROFILE_INVALID", InvitationError.MissingOrInvalidUserProfile)
  }

  @Test
  fun redeemInvitationMapsPatientSlotPrecondition() = runBlocking {
    assertPreconditionMaps("PATIENT_SLOT_TAKEN", InvitationError.PatientSlotAlreadyOccupied)
  }

  @Test
  fun redeemInvitationMapsCirclePrecondition() = runBlocking {
    assertPreconditionMaps("CIRCLE_UNAVAILABLE", InvitationError.InvalidOrUnavailableCareCircle)
  }

  @Test
  fun redeemInvitationIgnoresMessageWhenMappingPrecondition() = runBlocking {
    respondWithFailure(
        FirebaseFunctionsException.Code.FAILED_PRECONDITION,
        "User profile is in a circle that has expired",
        mapOf("reason" to "PATIENT_SLOT_TAKEN"),
    )

    assertSame(
        InvitationError.PatientSlotAlreadyOccupied,
        repository.redeemInvitation("valid-code").exceptionOrNull(),
    )
  }

  @Test
  fun redeemInvitationMapsUnrecognizedReasonToUnknown() = runBlocking {
    assertPreconditionIsUnknown(mapOf("reason" to "INVITATION_INVALID"))
  }

  @Test
  fun redeemInvitationMapsPreconditionWithoutReasonToUnknown() = runBlocking {
    assertPreconditionIsUnknown(null)
  }

  @Test
  fun redeemInvitationMapsMalformedPreconditionDetailsToUnknown() = runBlocking {
    assertPreconditionIsUnknown("INVITATION_EXPIRED")
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

  private fun respondWithFailure(
      code: FirebaseFunctionsException.Code,
      message: String,
      details: Any? = null,
  ) {
    `when`(callable.call(any()))
        .thenReturn(Tasks.forException(functionsException(code, message, details)))
  }

  private fun functionsException(
      code: FirebaseFunctionsException.Code,
      message: String,
      details: Any? = null,
  ): FirebaseFunctionsException =
      FirebaseFunctionsException::class
          .java
          .getConstructor(
              String::class.java,
              FirebaseFunctionsException.Code::class.java,
              Any::class.java,
          )
          .newInstance(message, code, details)

  private suspend fun assertPreconditionMaps(reason: String, expected: InvitationError) {
    respondWithFailure(
        FirebaseFunctionsException.Code.FAILED_PRECONDITION,
        "Precondition failed",
        mapOf("reason" to reason),
    )

    assertSame(expected, repository.redeemInvitation("valid-code").exceptionOrNull())
  }

  private suspend fun assertPreconditionIsUnknown(details: Any?) {
    val failure =
        functionsException(
            FirebaseFunctionsException.Code.FAILED_PRECONDITION,
            "Invitation has expired",
            details,
        )
    `when`(callable.call(any())).thenReturn(Tasks.forException(failure))

    val error = repository.redeemInvitation("valid-code").exceptionOrNull()

    assertSame(failure, (error as InvitationError.Unknown).source)
  }
}
