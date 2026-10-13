// This file was written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude
// Code.
package com.github.se.amber.data.invitation

import com.google.firebase.functions.FirebaseFunctions
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class InvitationRedemptionRepositoryProviderTest {
  private val functions: FirebaseFunctions = mockk()

  @Before
  fun setUp() {
    mockkObject(FirebaseFunctions.Companion)
    every { FirebaseFunctions.getInstance() } returns functions
  }

  @After
  fun tearDown() {
    unmockkObject(FirebaseFunctions.Companion)
  }

  @Test
  fun providerRetriesAfterMissingFirebaseAndSharesRepository() = runTest {
    // Building fails while the default Firebase app is not initialized
    val missingApp = IllegalStateException("Default FirebaseApp is not initialized")
    every { FirebaseFunctions.getInstance() } throws missingApp
    assertSame(
        missingApp,
        assertThrows(IllegalStateException::class.java) {
          InvitationRedemptionRepositoryProvider.repository
        },
    )

    // Once Firebase is available, the next access builds the repository
    every { FirebaseFunctions.getInstance() } returns functions
    val repository = InvitationRedemptionRepositoryProvider.repository

    assertEquals(FirebaseInvitationRedemptionRepository::class.java, repository.javaClass)
    assertSame(repository, InvitationRedemptionRepositoryProvider.repository)
    // Malformed codes are rejected before the function is called
    assertSame(InvitationError.InvalidCode, repository.redeemInvitation("").exceptionOrNull())
  }
}
