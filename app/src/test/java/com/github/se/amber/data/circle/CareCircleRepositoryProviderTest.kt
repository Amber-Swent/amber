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
import org.junit.Before
import org.junit.Test

class CareCircleRepositoryProviderTest {
  @Before
  fun setUp() {
    mockkStatic(FirebaseFirestore::class)
    every { FirebaseFirestore.getInstance() } returns mockk()
  }

  @After
  fun tearDown() {
    unmockkStatic(FirebaseFirestore::class)
  }

  @Test
  fun providerBuildsAndSharesFirebaseRepository() {
    val repository = CareCircleRepositoryProvider.repository

    assertEquals(FirebaseCareCircleRepository::class.java, repository.javaClass)
    assertSame(repository, CareCircleRepositoryProvider.repository)
  }
}
