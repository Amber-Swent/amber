// Written by Alaabrine, with assistance from Cursor (Grok).
// Modified by Alaabrine, with assistance from Cursor (Grok).

package com.github.se.amber.data.circle

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class ActiveCircleRepositoryProviderTest {
  private val fake: ActiveCircleRepository = mockk()

  @After
  fun tearDown() {
    unmockkStatic(FirebaseApp::class)
  }

  @Test
  fun repositoryIsBuiltByFactory() {
    val provider = ActiveCircleRepositoryProvider { fake }

    assertSame(fake, provider.repository)
  }

  @Test
  fun repositoryIsBuiltOnlyOnce() {
    var builds = 0
    val provider = ActiveCircleRepositoryProvider {
      builds++
      fake
    }

    provider.repository
    provider.repository

    assertEquals(1, builds)
  }

  @Test
  fun repositoryIsRebuiltAfterFactoryFailure() {
    var attempts = 0
    val provider = ActiveCircleRepositoryProvider {
      if (attempts++ == 0) throw IllegalStateException("DataStore not ready")
      fake
    }

    runCatching { provider.repository }

    assertSame(fake, provider.repository)
  }

  @Test
  fun contextBuildsDataStoreRepository() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = ActiveCircleRepositoryProvider.dataStoreRepository(context)
    val circles = listOf("circle-a", "circle-b")

    repository.setActiveCircle("uid-context-test", "circle-b")

    assertEquals("circle-b", repository.activeCircleId("uid-context-test", circles).first())
  }

  @Test
  fun defaultFactoryBuildsDataStoreRepository() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val app = mockk<FirebaseApp>()
    mockkStatic(FirebaseApp::class)
    every { FirebaseApp.getInstance() } returns app
    every { app.applicationContext } returns context

    ActiveCircleRepositoryProvider().repository

    verify(exactly = 1) { app.applicationContext }
  }
}
