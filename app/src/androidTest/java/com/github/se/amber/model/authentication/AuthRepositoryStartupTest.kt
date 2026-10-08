// This file was written with the aid of AI
package com.github.se.amber.model.authentication

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.se.amber.MainActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthRepositoryStartupTest {

  @Test
  fun appStartupInitializesFirebaseBeforeRepositoryAccess() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    // Do not initialize Firebase: its startup provider must already have done so
    val firebaseApp = FirebaseApp.getInstance() // Retrieves the default Firebase app

    // Check the configuration and context
    val configuredOptions = checkNotNull(FirebaseOptions.fromResource(context))
    assertEquals(FirebaseApp.DEFAULT_APP_NAME, firebaseApp.name)
    assertEquals(configuredOptions.applicationId, firebaseApp.options.applicationId)
    assertSame(context, firebaseApp.applicationContext)

    // Launch Activity and access the repository
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        // This creates the real repository and Credential Manager without signing in or out.
        val repository = AuthRepositoryProvider.repository
        assertTrue(repository is AuthRepositoryFirebase)
        assertSame(activity.applicationContext, firebaseApp.applicationContext)
      }
    }
  }
}
