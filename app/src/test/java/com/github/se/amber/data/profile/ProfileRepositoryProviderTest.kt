// This file was written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude
// Code.
package com.github.se.amber.data.profile

import com.google.firebase.auth.FirebaseAuth
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

class ProfileRepositoryProviderTest {
  @Before
  fun setUp() {
    mockkStatic(FirebaseAuth::class, FirebaseFirestore::class)
    every { FirebaseAuth.getInstance() } returns mockk()
    every { FirebaseFirestore.getInstance() } returns mockk()
  }

  @After
  fun tearDown() {
    unmockkStatic(FirebaseAuth::class, FirebaseFirestore::class)
  }

  @Test
  fun providerBuildsAndSharesFirebaseRepository() {
    val repository = ProfileRepositoryProvider.repository

    assertEquals(FirebaseProfileRepository::class.java, repository.javaClass)
    assertSame(repository, ProfileRepositoryProvider.repository)
  }
}
