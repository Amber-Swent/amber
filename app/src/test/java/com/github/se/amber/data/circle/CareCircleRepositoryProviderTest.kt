// This file was written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude
// Code.
package com.github.se.amber.data.circle

import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CareCircleRepositoryProviderTest {
  private val fake: CareCircleRepository = mockk()

  @After
  fun tearDown() {
    unmockkStatic(FirebaseFirestore::class)
  }

  @Test
  fun repositoryIsBuiltByFactory() {
    val provider = CareCircleRepositoryProvider { fake }

    assertSame(fake, provider.repository)
  }

  @Test
  fun repositoryIsBuiltOnlyOnce() {
    var builds = 0
    val provider = CareCircleRepositoryProvider {
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
    val provider = CareCircleRepositoryProvider {
      if (attempts++ == 0) throw IllegalStateException("Firebase not initialized")
      fake
    }

    runCatching { provider.repository }

    assertSame(fake, provider.repository)
  }

  @Test
  fun defaultFactoryBuildsFirebaseRepository() {
    mockkStatic(FirebaseFirestore::class)
    every { FirebaseFirestore.getInstance() } returns mockk()

    val provider = CareCircleRepositoryProvider()

    assertEquals(FirebaseCareCircleRepository::class.java, provider.repository.javaClass)
  }
}
