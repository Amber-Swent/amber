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
import org.junit.Before
import org.junit.Test

class InvitationRedemptionRepositoryProviderTest {
  @Before
  fun setUp() {
    mockkObject(FirebaseFunctions.Companion)
    every { FirebaseFunctions.getInstance() } returns mockk()
  }

  @After
  fun tearDown() {
    unmockkObject(FirebaseFunctions.Companion)
  }

  @Test
  fun providerBuildsAndSharesFirebaseRepository() {
    val repository = InvitationRedemptionRepositoryProvider.repository

    assertEquals(FirebaseInvitationRedemptionRepository::class.java, repository.javaClass)
    assertSame(repository, InvitationRedemptionRepositoryProvider.repository)
  }
}
