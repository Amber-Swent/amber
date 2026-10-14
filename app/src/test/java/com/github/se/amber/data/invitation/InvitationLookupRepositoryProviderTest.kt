// This file was written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude
// Code.
package com.github.se.amber.data.invitation

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class InvitationLookupRepositoryProviderTest {
  private val fake: InvitationLookupRepository = mockk()

  @After
  fun tearDown() {
    unmockkStatic(FirebaseAuth::class, FirebaseFirestore::class)
  }

  @Test
  fun repositoryIsBuiltByFactory() {
    val provider = InvitationLookupRepositoryProvider { fake }

    assertSame(fake, provider.repository)
  }

  @Test
  fun repositoryIsBuiltOnlyOnce() {
    var builds = 0
    val provider = InvitationLookupRepositoryProvider {
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
    val provider = InvitationLookupRepositoryProvider {
      if (attempts++ == 0) throw IllegalStateException("Firebase not initialized")
      fake
    }

    runCatching { provider.repository }

    assertSame(fake, provider.repository)
  }

  @Test
  fun defaultFactoryBuildsFirebaseRepository() {
    mockkStatic(FirebaseAuth::class, FirebaseFirestore::class)
    every { FirebaseAuth.getInstance() } returns mockk()
    every { FirebaseFirestore.getInstance() } returns mockk()

    val provider = InvitationLookupRepositoryProvider()

    assertEquals(FirebaseInvitationLookupRepository::class.java, provider.repository.javaClass)
  }
}
