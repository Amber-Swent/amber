// This file was written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude
// Code.
package com.github.se.amber.data.circle

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

class CareCircleRepositoryProviderTest {
  private val firestore: FirebaseFirestore = mockk()

  @Before
  fun setUp() {
    mockkStatic(FirebaseFirestore::class)
    every { FirebaseFirestore.getInstance() } returns firestore
  }

  @After
  fun tearDown() {
    unmockkStatic(FirebaseFirestore::class)
  }

  @Test
  fun providerRetriesAfterMissingFirebaseAndSharesRepository() = runTest {
    // Building fails while the default Firebase app is not initialized
    val missingApp = IllegalStateException("Default FirebaseApp is not initialized")
    every { FirebaseFirestore.getInstance() } throws missingApp
    assertSame(
        missingApp,
        assertThrows(IllegalStateException::class.java) { CareCircleRepositoryProvider.repository },
    )

    // Once Firebase is available, the next access builds the repository
    every { FirebaseFirestore.getInstance() } returns firestore
    val repository = CareCircleRepositoryProvider.repository

    assertEquals(FirebaseCareCircleRepository::class.java, repository.javaClass)
    assertSame(repository, CareCircleRepositoryProvider.repository)
    // Blank ids are rejected before Firestore is read
    assertSame(CareCircleError.InvalidCircleId, repository.getCircle("").exceptionOrNull())
  }
}
