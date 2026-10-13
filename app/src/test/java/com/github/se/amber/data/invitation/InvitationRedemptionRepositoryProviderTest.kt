// This file was written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude
// Code.
package com.github.se.amber.data.invitation

import com.google.firebase.functions.FirebaseFunctions
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class InvitationRedemptionRepositoryProviderTest {
  private val fake: InvitationRedemptionRepository = mockk()

  @After
  fun tearDown() {
    unmockkObject(FirebaseFunctions.Companion)
  }

  @Test
  fun repositoryIsBuiltByFactory() {
    val provider = InvitationRedemptionRepositoryProvider { fake }

    assertSame(fake, provider.repository)
  }

  @Test
  fun repositoryIsBuiltOnlyOnce() {
    var builds = 0
    val provider = InvitationRedemptionRepositoryProvider {
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
    val provider = InvitationRedemptionRepositoryProvider {
      if (attempts++ == 0) throw IllegalStateException("Firebase not initialized")
      fake
    }

    runCatching { provider.repository }

    assertSame(fake, provider.repository)
  }

  @Test
  fun defaultFactoryBuildsFirebaseRepository() {
    mockkObject(FirebaseFunctions.Companion)
    every { FirebaseFunctions.getInstance() } returns mockk()

    val provider = InvitationRedemptionRepositoryProvider()

    assertEquals(FirebaseInvitationRedemptionRepository::class.java, provider.repository.javaClass)
  }
}
