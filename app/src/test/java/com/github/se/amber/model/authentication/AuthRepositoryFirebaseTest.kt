// This file was written with the aid of AI
package com.github.se.amber.model.authentication

import android.app.Application
import android.os.Bundle
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.PasswordCredential
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE)
class AuthRepositoryFirebaseTest {
  private lateinit var auth: FirebaseAuth
  private lateinit var helper: GoogleSignInHelper
  private lateinit var repository: AuthRepositoryFirebase
  private lateinit var user: FirebaseUser
  private var currentFirebaseUser: FirebaseUser? = null
  private var cleanupCompleted = false
  private var cleanup: suspend () -> Unit = {}

  private val credential =
      CustomCredential(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL, Bundle())
  private val firebaseCredential = GoogleAuthProvider.getCredential("local-id-token", null)

  @Before
  fun setUp() {
    auth = mock(FirebaseAuth::class.java)
    helper = mock(GoogleSignInHelper::class.java)
    user = mock(FirebaseUser::class.java)
    `when`(user.uid).thenReturn("user-123")
    `when`(auth.currentUser).thenAnswer { currentFirebaseUser }
    `when`(helper.getAuthCredential(credential.data)).thenReturn(firebaseCredential)
    doAnswer {
          currentFirebaseUser = null
          null
        }
        .`when`(auth)
        .signOut()

    val manager =
        object :
            CredentialManager by CredentialManager.create(RuntimeEnvironment.getApplication()) {
          override suspend fun clearCredentialState(request: ClearCredentialStateRequest) {
            cleanup()
            cleanupCompleted = true
          }
        }
    repository = AuthRepositoryFirebase(manager, helper, auth)
  }

  @Test
  fun currentUserIsNullWhenSignedOut() {
    assertNull(repository.currentUser)
  }

  @Test
  fun currentUserMapsUidAndReflectsLaterSignOut() {
    currentFirebaseUser = user
    assertEquals(AuthUser("user-123"), repository.currentUser)

    currentFirebaseUser = null
    assertNull(repository.currentUser)
  }

  @Test
  fun signInReturnsAppUserFromFirebaseResult() =
      // runBlocking can call suspend functions (here, signInWithGoogle)
      runBlocking {
        val firebaseResult = authResult(user)
        // Tells Mockito: when Firebase is called with this credential, return the given successful
        // task
        `when`(auth.signInWithCredential(firebaseCredential))
            .thenReturn(Tasks.forResult(firebaseResult))

        val result = repository.signInWithGoogle(credential) // Will use mocked dependencies

        assertEquals(AuthUser("user-123"), result.getOrThrow())
        verify(helper).getAuthCredential(credential.data)
        verify(auth).signInWithCredential(firebaseCredential)
        assertFalse(cleanupCompleted)
      }

  @Test
  fun passwordCredentialIsRejectedBeforeConversion() = runBlocking {
    val result = repository.signInWithGoogle(PasswordCredential("user", "password"))

    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    assertEquals("Unsupported sign-in credential", result.exceptionOrNull()?.message)
    verifyNoInteractions(helper, auth)
  }

  @Test
  fun unsupportedCustomCredentialIsRejectedBeforeConversion() = runBlocking {
    val result = repository.signInWithGoogle(CustomCredential("unsupported-credential", Bundle()))

    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    assertEquals("Unsupported sign-in credential", result.exceptionOrNull()?.message)
    verifyNoInteractions(helper, auth)
  }

  @Test
  fun conversionFailureIsReturnedWithoutCallingFirebase() = runBlocking {
    val failure = IllegalArgumentException("Malformed credential")
    `when`(helper.getAuthCredential(credential.data)).thenThrow(failure)

    val result = repository.signInWithGoogle(credential)

    assertSame(failure, result.exceptionOrNull())
    verifyNoInteractions(auth)
  }

  @Test
  fun failedFirebaseTaskPreservesOriginalException() = runBlocking {
    val failure = IllegalStateException("Authentication failed")
    `when`(auth.signInWithCredential(firebaseCredential))
        .thenReturn(Tasks.forException<AuthResult>(failure))

    assertSame(failure, repository.signInWithGoogle(credential).exceptionOrNull())
  }

  @Test
  fun successfulFirebaseTaskWithoutUserReturnsFailure() = runBlocking {
    val firebaseResult = authResult(null)
    `when`(auth.signInWithCredential(firebaseCredential))
        .thenReturn(Tasks.forResult(firebaseResult))

    val result = repository.signInWithGoogle(credential)

    assertTrue(result.exceptionOrNull() is IllegalStateException)
  }

  @Test
  fun cancelledFirebaseTaskPropagatesCancellation() {
    `when`(auth.signInWithCredential(firebaseCredential))
        .thenReturn(Tasks.forCanceled<AuthResult>())

    assertThrows(CancellationException::class.java) {
      runBlocking { repository.signInWithGoogle(credential) }
    }
  }

  @Test
  fun cancellingCallerDoesNotReturnFailureAsNormalResult() = runBlocking {
    val task = TaskCompletionSource<AuthResult>()
    `when`(auth.signInWithCredential(firebaseCredential)).thenReturn(task.task)
    var returnedNormally = false

    val job =
        launch(start = CoroutineStart.UNDISPATCHED) {
          repository.signInWithGoogle(credential)
          returnedNormally = true
        }
    assertTrue(job.isActive)

    job.cancel()
    job.join()

    assertTrue(job.isCancelled)
    assertFalse(returnedNormally)
  }

  @Test
  fun signOutEndsFirebaseSessionBeforeClearingProviderState() = runBlocking {
    currentFirebaseUser = user
    cleanup = { assertNull(repository.currentUser) }

    val result = repository.signOut()

    assertEquals(Unit, result.getOrThrow())
    assertNull(repository.currentUser)
    assertTrue(cleanupCompleted)
  }

  @Test
  fun firebaseSignOutFailureSkipsProviderCleanup() = runBlocking {
    currentFirebaseUser = user
    val failure = IllegalStateException("Sign-out failed")
    doThrow(failure).`when`(auth).signOut()
    cleanup = { throw AssertionError("Cleanup must not run after Firebase failure") }

    val result = repository.signOut()

    assertSame(failure, result.exceptionOrNull())
    assertEquals(AuthUser("user-123"), repository.currentUser)
    assertFalse(cleanupCompleted)
  }

  @Test
  fun cleanupFailureLeavesFirebaseSignedOut() = runBlocking {
    currentFirebaseUser = user
    val failure = IllegalStateException("Cleanup failed")
    cleanup = { throw failure }

    val result = repository.signOut()

    assertSame(failure, result.exceptionOrNull())
    assertNull(repository.currentUser)
    assertFalse(cleanupCompleted)
  }

  @Test
  fun cleanupCancellationIsRethrownAndLeavesFirebaseSignedOut() {
    currentFirebaseUser = user
    val cancellation = CancellationException("Cleanup cancelled")
    cleanup = { throw cancellation }

    val thrown =
        assertThrows(CancellationException::class.java) { runBlocking { repository.signOut() } }

    assertSame(cancellation, thrown)
    assertNull(repository.currentUser)
    assertFalse(cleanupCompleted)
  }

  // Check that both login and logout work when in the same sequence
  @Test
  fun signInThenSignOutSucceedsInSameSequence() = runBlocking {
    val firebaseResult = authResult(user)
    `when`(auth.signInWithCredential(firebaseCredential)).thenAnswer {
      currentFirebaseUser = user
      Tasks.forResult(firebaseResult)
    }

    val signInResult = repository.signInWithGoogle(credential)

    assertEquals(AuthUser("user-123"), signInResult.getOrThrow())
    assertEquals(AuthUser("user-123"), repository.currentUser)

    val signOutResult = repository.signOut()

    assertEquals(Unit, signOutResult.getOrThrow())
    assertNull(repository.currentUser)
    assertTrue(cleanupCompleted)
  }

  private fun authResult(firebaseUser: FirebaseUser?): AuthResult {
    val result = mock(AuthResult::class.java)
    `when`(result.user).thenReturn(firebaseUser)
    return result
  }
}
