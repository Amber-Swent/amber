// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment

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

class AppointmentRepositoryProviderTest {
  private val fake: AppointmentRepository = FakeAppointmentRepository()

  @After
  fun tearDown() {
    unmockkStatic(FirebaseAuth::class, FirebaseFirestore::class)
  }

  @Test
  fun repositoryIsBuiltOnlyOnce() {
    var builds = 0
    val provider = AppointmentRepositoryProvider {
      builds++
      fake
    }

    assertSame(fake, provider.repository)
    assertSame(fake, provider.repository)
    assertEquals(1, builds)
  }

  @Test
  fun repositoryIsBuiltAgainAfterAFactoryFailure() {
    var attempts = 0
    val provider = AppointmentRepositoryProvider {
      if (attempts++ == 0) throw IllegalStateException("Firebase not initialized")
      fake
    }

    runCatching { provider.repository }

    assertSame(fake, provider.repository)
  }

  @Test
  fun defaultFactoryBuildsTheFirebaseRepository() {
    mockkStatic(FirebaseAuth::class, FirebaseFirestore::class)
    every { FirebaseAuth.getInstance() } returns mockk()
    every { FirebaseFirestore.getInstance() } returns mockk()

    val provider = AppointmentRepositoryProvider()

    assertEquals(FirebaseAppointmentRepository::class.java, provider.repository.javaClass)
  }
}
