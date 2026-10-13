// This file was written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude
// Code.
package com.github.se.amber.data.invitation

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class InvitationCreationRepositoryProviderTest {
  private val auth: FirebaseAuth = mockk()
  private val firestore: FirebaseFirestore = mockk()

  @Before
  fun setUp() {
    mockkStatic(FirebaseAuth::class, FirebaseFirestore::class)
    every { FirebaseAuth.getInstance() } returns auth
    every { FirebaseFirestore.getInstance() } returns firestore
    every { auth.currentUser } returns null
  }

  @After
  fun tearDown() {
    unmockkStatic(FirebaseAuth::class, FirebaseFirestore::class)
  }

  @Test
  fun providerRetriesAfterMissingFirebaseAndSharesRepository() = runTest {
    // Building fails while the default Firebase app is not initialized
    val missingApp = IllegalStateException("Default FirebaseApp is not initialized")
    every { FirebaseAuth.getInstance() } throws missingApp
    assertSame(
        missingApp,
        assertThrows(IllegalStateException::class.java) {
          InvitationCreationRepositoryProvider.repository
        },
    )

    // Once Firebase is available, the next access builds the repository
    every { FirebaseAuth.getInstance() } returns auth
    val repository = InvitationCreationRepositoryProvider.repository

    assertEquals(FirebaseInvitationCreationRepository::class.java, repository.javaClass)
    assertSame(repository, InvitationCreationRepositoryProvider.repository)
    // The repository reads the default FirebaseAuth instance
    assertSame(InvitationCreationResult.NotAuthenticated, repository.createInvitation("circle"))
  }
}
