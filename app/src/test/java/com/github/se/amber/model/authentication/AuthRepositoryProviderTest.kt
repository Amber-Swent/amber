// This file was written with the aid of AI
package com.github.se.amber.model.authentication

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE)
class AuthRepositoryProviderTest {

  /**
   * This test verifies that:
   * - Access fails if the default Firebase is unavailable
   * - Once Firebase is available, provider creates a Credential Manager and constructs the
   *   repository
   * - Subsequent accesses return the same repository
   * - Repository reads the configured Firebase authentication state
   */
  @Test
  fun providerRetriesAfterMissingFirebaseAndSharesRepository() {
    // Mockito mocks
    val firebaseApp = mock(FirebaseApp::class.java)
    val auth = mock(FirebaseAuth::class.java)
    val user = mock(FirebaseUser::class.java)
    val missingApp = IllegalStateException("Default Firebase app is not initialized")
    `when`(firebaseApp.applicationContext).thenReturn(RuntimeEnvironment.getApplication())
    `when`(user.uid).thenReturn("provider-user")
    `when`(auth.currentUser).thenReturn(user)

    // Replace Firebase's static access methods
    mockStatic(FirebaseApp::class.java).use { apps ->
      mockStatic(FirebaseAuth::class.java).use { authentication ->
        // Simulates Firebase not being initialized
        apps.`when`<FirebaseApp> { FirebaseApp.getInstance() }.thenThrow(missingApp)
        authentication.`when`<FirebaseAuth> { FirebaseAuth.getInstance() }.thenReturn(auth)

        val thrown =
            assertThrows(IllegalStateException::class.java) { AuthRepositoryProvider.repository }
        assertSame(missingApp, thrown)

        // Simulates Firebase becoming available
        apps.`when`<FirebaseApp> { FirebaseApp.getInstance() }.thenReturn(firebaseApp)
        val repository = AuthRepositoryProvider.repository

        // Verify construction and caching
        assertTrue(repository is AuthRepositoryFirebase)
        assertEquals(AuthUser("provider-user"), repository.currentUser)
        assertSame(repository, AuthRepositoryProvider.repository)

        `when`(auth.currentUser).thenReturn(null)
        assertNull(repository.currentUser)
      }
    }
  }
}
