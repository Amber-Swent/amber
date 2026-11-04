// Written by Neha Chakraborty, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.account

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Checks how [AccountDeletionRepositoryProvider] builds its repository, on Robolectric, where
 * Firebase is initialized by hand like the app does at startup. Nothing here contacts Firebase.
 */
@RunWith(RobolectricTestRunner::class)
class AccountDeletionRepositoryProviderTest {

  private val context: Context = RuntimeEnvironment.getApplication()

  @Before
  fun initializeFirebase() {
    if (FirebaseApp.getApps(context).isEmpty()) {
      FirebaseApp.initializeApp(
          context,
          FirebaseOptions.Builder()
              .setApplicationId("1:123456789012:android:abcdef123456")
              .setApiKey("test-api-key")
              .setProjectId("amber-test")
              .build(),
      )
    }
  }

  @Test
  fun theDefaultFactoryBuildsAFirebaseRepository() {
    // fakes: the app-wide media and auth repositories are left for their own tests to build
    val repository =
        AccountDeletionRepositoryProvider.firebaseRepository(
            mediaRepository = mockk(),
            authRepository = mockk(),
        )

    assertTrue(repository is FirebaseAccountDeletionRepository)
  }

  @Test
  fun repositoryIsBuiltOnceByFactoryAndAgainAfterAFailure() {
    val fake: AccountDeletionRepository = mockk()
    var attempts = 0
    val provider = AccountDeletionRepositoryProvider {
      if (attempts++ == 0) throw IllegalStateException("Firebase not initialized")
      fake
    }

    runCatching { provider.repository }

    assertSame(fake, provider.repository)
    assertSame(fake, provider.repository)
    assertEquals(2, attempts)
  }
}
