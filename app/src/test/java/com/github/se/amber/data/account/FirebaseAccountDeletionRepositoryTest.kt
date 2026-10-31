// Written by Neha Chakraborty, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.account

import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.FirebaseFunctionsException.Code
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class FirebaseAccountDeletionRepositoryTest {
  private lateinit var callable: HttpsCallableReference
  private lateinit var repository: FirebaseAccountDeletionRepository
  private var clears = 0
  private var clearLocalData: suspend () -> Unit = {}

  @Before
  fun setUp() {
    val functions = mock(FirebaseFunctions::class.java)
    callable = mock(HttpsCallableReference::class.java)
    `when`(functions.getHttpsCallable("deleteAccount")).thenReturn(callable)
    val localData = LocalAccountData {
      clears++
      clearLocalData()
    }
    repository = FirebaseAccountDeletionRepository(localData, functions)
  }

  private fun respondWith(data: Any?) {
    val result = HttpsCallableResult::class.java.getConstructor(Any::class.java).newInstance(data)
    `when`(callable.call()).thenReturn(Tasks.forResult(result))
  }

  private fun functionsException(code: Code, details: Any? = null): FirebaseFunctionsException =
      FirebaseFunctionsException::class
          .java
          .getConstructor(String::class.java, Code::class.java, Any::class.java)
          .newInstance(code.name, code, details)

  /** A request the SDK couldn't send: it wraps the IOException, as it does when offline. */
  private fun offlineException(): FirebaseFunctionsException =
      FirebaseFunctionsException::class
          .java
          .getConstructor(
              String::class.java,
              Code::class.java,
              Any::class.java,
              Throwable::class.java,
          )
          .newInstance("INTERNAL", Code.INTERNAL, null, IOException("Offline"))

  @Test
  fun confirmedDeletionClearsTheDeviceAndGivesTheFunctionTimeToFinish() = runBlocking {
    respondWith(mapOf("deleted" to true))

    assertTrue(repository.deleteAccount().isSuccess)

    assertEquals(1, clears)
    verify(callable).setTimeout(550L, TimeUnit.SECONDS)
  }

  @Test
  fun everyBackendFailureMapsToItsErrorAndLeavesTheDeviceAlone() = runBlocking {
    val expected =
        listOf(
            functionsException(Code.UNAVAILABLE) to AccountDeletionError.Network,
            functionsException(Code.DEADLINE_EXCEEDED) to AccountDeletionError.Network,
            offlineException() to AccountDeletionError.Network,
            functionsException(Code.UNAUTHENTICATED) to
                AccountDeletionError.ReauthenticationRequired,
            functionsException(
                Code.FAILED_PRECONDITION,
                details = mapOf("reason" to "RECENT_SIGN_IN_REQUIRED"),
            ) to AccountDeletionError.ReauthenticationRequired,
            functionsException(Code.PERMISSION_DENIED) to AccountDeletionError.PermissionDenied,
        )
    for ((failure, error) in expected) {
      `when`(callable.call()).thenReturn(Tasks.forException(failure))

      assertSame(failure.code.name, error, repository.deleteAccount().exceptionOrNull())
    }

    assertEquals(0, clears)
  }

  @Test
  fun unexpectedFailuresAreUnknownAndLeaveTheDeviceAlone() = runBlocking {
    val failures =
        listOf(
            functionsException(Code.INTERNAL),
            functionsException(Code.NOT_FOUND),
            functionsException(Code.FAILED_PRECONDITION, details = mapOf("reason" to "OTHER")),
            functionsException(Code.FAILED_PRECONDITION, details = "not a map"),
            IllegalStateException("Boom"),
        )
    for (failure in failures) {
      `when`(callable.call()).thenReturn(Tasks.forException(failure))

      val error = repository.deleteAccount().exceptionOrNull() as AccountDeletionError.Unknown

      // not the same instance: coroutines may copy an exception they rethrow
      assertEquals(failure.message, error.source.message)
    }

    assertEquals(0, clears)
  }

  @Test
  fun anUnconfirmedResponseLeavesTheDeviceAlone() = runBlocking {
    for (response in listOf(null, "deleted", mapOf("deleted" to false), emptyMap<String, Any>())) {
      respondWith(response)

      val error = repository.deleteAccount().exceptionOrNull() as AccountDeletionError.Unknown

      assertEquals("deleteAccount returned an invalid response", error.source.message)
    }

    assertEquals(0, clears)
  }

  @Test
  fun cancellingBeforeTheConfirmationLeavesTheDeviceAlone() = runBlocking {
    `when`(callable.call()).thenReturn(Tasks.forException(CancellationException("Cancelled")))

    assertThrows(CancellationException::class.java) { runBlocking { repository.deleteAccount() } }

    assertEquals(0, clears)
  }

  @Test
  fun aDeviceThatCouldNotBeClearedIsReportedAsSuch() = runBlocking {
    respondWith(mapOf("deleted" to true))
    val failure = IOException("Files left")
    clearLocalData = { throw failure }

    val error = repository.deleteAccount().exceptionOrNull()

    assertEquals(
        failure.message,
        (error as AccountDeletionError.LocalDataNotCleared).source.message,
    )
  }
}
