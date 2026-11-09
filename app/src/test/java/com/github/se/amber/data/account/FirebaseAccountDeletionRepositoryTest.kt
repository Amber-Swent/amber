// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.account

import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.FirebaseFunctionsException.Code
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

class FirebaseAccountDeletionRepositoryTest {
  private lateinit var callable: HttpsCallableReference
  private lateinit var auth: FirebaseAuth
  private lateinit var user: FirebaseUser
  private lateinit var repository: FirebaseAccountDeletionRepository
  private var clears = 0
  private var clearLocalData: suspend () -> Unit = {}
  private var markerSaves = true
  private val marker =
      object : DeletionMarker {
        override var isSet = false

        override fun set() {
          if (!markerSaves) throw IOException("Disk full")
          isSet = true
        }

        override fun remove() {
          isSet = false
        }
      }

  @Before
  fun setUp() {
    val functions = mock(FirebaseFunctions::class.java)
    callable = mock(HttpsCallableReference::class.java)
    `when`(functions.getHttpsCallable("deleteAccount")).thenReturn(callable)
    val localData = LocalAccountData {
      clears++
      clearLocalData()
    }
    auth = mock(FirebaseAuth::class.java)
    user = mock(FirebaseUser::class.java)
    `when`(auth.currentUser).thenReturn(user)
    repository = FirebaseAccountDeletionRepository(localData, marker, functions, auth)
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
        )
    for ((failure, error) in expected) {
      `when`(callable.call()).thenReturn(Tasks.forException(failure))

      assertSame(failure.code.name, error, repository.deleteAccount().exceptionOrNull())
    }

    assertEquals(0, clears)
  }

  @Test
  fun unexpectedFailuresAndUnconfirmedResponsesAreUnknownAndLeaveTheDeviceAlone() = runBlocking {
    val failures =
        listOf(
            functionsException(Code.INTERNAL),
            functionsException(Code.NOT_FOUND),
            functionsException(Code.PERMISSION_DENIED),
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

    for (response in listOf(null, "deleted", mapOf("deleted" to false), emptyMap<String, Any>())) {
      respondWith(response)

      val error = repository.deleteAccount().exceptionOrNull() as AccountDeletionError.Unknown

      assertEquals("deleteAccount returned an invalid response", error.source.message)
    }

    assertEquals(0, clears)
  }

  @Test
  fun cancellingBeforeTheConfirmationLeavesTheDeviceAlone() {
    `when`(callable.call()).thenReturn(Tasks.forException(CancellationException("Cancelled")))

    assertThrows(CancellationException::class.java) { runBlocking { repository.deleteAccount() } }

    assertEquals(0, clears)
  }

  @Test
  fun theMarkerIsSetBeforeTheBackendAndOnlyRemovedWhenItRefusedToStart() = runBlocking {
    // whether the backend may have started deleting the account
    val failures =
        listOf(
            functionsException(Code.UNAVAILABLE) to true,
            offlineException() to true,
            functionsException(Code.INTERNAL) to true,
            functionsException(Code.UNAUTHENTICATED) to false,
            functionsException(
                Code.FAILED_PRECONDITION,
                details = mapOf("reason" to "RECENT_SIGN_IN_REQUIRED"),
            ) to false,
        )
    for ((failure, mayHaveStarted) in failures) {
      marker.remove()
      var markedWhenCalled = false
      `when`(callable.call()).thenAnswer {
        markedWhenCalled = marker.isSet
        Tasks.forException<HttpsCallableResult>(failure)
      }

      repository.deleteAccount()

      assertTrue(failure.code.name, markedWhenCalled)
      assertEquals(failure.code.name, mayHaveStarted, marker.isSet)
    }
  }

  @Test
  fun aMarkerThatCouldNotBeSavedStopsTheDeletionBeforeTheBackend() = runBlocking {
    markerSaves = false

    val error = repository.deleteAccount().exceptionOrNull() as AccountDeletionError.Unknown

    assertEquals("Disk full", error.source.message)
    verifyNoInteractions(callable)
    assertEquals(0, clears)
  }

  @Test
  fun anInterruptedDeletionClearsTheDeviceOnlyIfMarkedAndTheAccountIsGone() = runBlocking {
    val gone = FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "Gone")
    `when`(user.reload()).thenReturn(Tasks.forException(gone))

    repository.finishInterruptedDeletion()
    assertEquals(0, clears)
    verifyNoInteractions(user)

    marker.set()
    repository.finishInterruptedDeletion()
    assertEquals(1, clears)

    `when`(auth.currentUser).thenReturn(null)
    repository.finishInterruptedDeletion()
    assertEquals(2, clears)
    verifyNoInteractions(callable)
  }

  @Test
  fun anAccountThatMayStillExistKeepsItsDataAndItsMarker() = runBlocking {
    marker.set()
    val answers =
        listOf(
            Tasks.forResult<Void>(null),
            Tasks.forException(FirebaseNetworkException("Offline")),
            Tasks.forException(FirebaseAuthInvalidUserException("ERROR_USER_DISABLED", "Disabled")),
        )
    for (answer in answers) {
      `when`(user.reload()).thenReturn(answer)

      repository.finishInterruptedDeletion()
    }

    assertEquals(0, clears)
    assertTrue(marker.isSet)
  }

  @Test
  fun cancellingWhileCheckingTheAccountOrClearingTheDeviceIsNotReportedAsAFailure() {
    marker.set()
    `when`(user.reload()).thenReturn(Tasks.forException(CancellationException("Cancelled")))
    assertThrows(CancellationException::class.java) {
      runBlocking { repository.finishInterruptedDeletion() }
    }
    assertEquals(0, clears)

    clearLocalData = { throw CancellationException("Cancelled") }
    assertThrows(CancellationException::class.java) { runBlocking { repository.clearLocalData() } }
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

  @Test
  fun clearLocalDataRetriesTheDeviceWithoutCallingTheBackend() = runBlocking {
    val failure = IOException("Files left")
    clearLocalData = { throw failure }
    val error = repository.clearLocalData().exceptionOrNull()
    assertEquals(
        failure.message,
        (error as AccountDeletionError.LocalDataNotCleared).source.message,
    )

    clearLocalData = {}
    assertTrue(repository.clearLocalData().isSuccess)

    assertEquals(2, clears)
    verifyNoInteractions(callable)
  }

  @Test
  fun aGoneAccountThatCouldNotBeClearedIsClearedAgainOnTheNextLaunch() = runBlocking {
    clearLocalData = { throw IOException("Files left") }

    val error = repository.clearGoneAccount().exceptionOrNull()

    assertTrue(error is AccountDeletionError.LocalDataNotCleared)
    assertTrue("the next launch must find the marker", marker.isSet)

    // the next launch: the user was signed out, so nobody is signed in
    `when`(auth.currentUser).thenReturn(null)
    clearLocalData = {}
    repository.finishInterruptedDeletion()

    assertEquals(2, clears)
    verifyNoInteractions(callable)
  }

  @Test
  fun aGoneAccountIsClearedEvenIfTheMarkerCannotBeSaved() = runBlocking {
    markerSaves = false

    assertTrue(repository.clearGoneAccount().isSuccess)

    assertEquals(1, clears)
  }

  @Test
  fun twoClearsAtOnceRunOneAfterTheOther() = runBlocking {
    val firstStarted = CompletableDeferred<Unit>()
    val firstMayFinish = CompletableDeferred<Unit>()
    val running = AtomicInteger()
    val mostAtOnce = AtomicInteger()
    clearLocalData = {
      mostAtOnce.accumulateAndGet(running.incrementAndGet(), ::maxOf)
      if (firstStarted.complete(Unit)) firstMayFinish.await()
      running.decrementAndGet()
    }

    val first = async(Dispatchers.Default) { repository.clearLocalData() }
    firstStarted.await()
    val second = async(Dispatchers.Default) { repository.clearLocalData() }
    delay(SECOND_CLEAR_START_MS) // time for the second to start, were it not waiting
    firstMayFinish.complete(Unit)

    assertTrue(first.await().isSuccess && second.await().isSuccess)
    assertEquals(BOTH_CLEARS, clears)
    assertEquals(1, mostAtOnce.get())
  }

  private companion object {
    const val SECOND_CLEAR_START_MS = 100L
    const val BOTH_CLEARS = 2
  }
}
