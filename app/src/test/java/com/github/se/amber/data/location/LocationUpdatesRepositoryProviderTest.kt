// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.location

import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class LocationUpdatesRepositoryProviderTest {

  private val fake =
      object : LocationUpdatesRepository {
        override fun hasPermission() = false

        override fun observeLocation() = emptyFlow<LocationUpdate>()
      }

  @Test
  fun repositoryIsBuiltOnlyOnceByFactory() {
    var builds = 0
    val provider = LocationUpdatesRepositoryProvider {
      builds++
      fake
    }

    assertSame(fake, provider.repository)
    assertSame(fake, provider.repository)
    assertEquals(1, builds)
  }

  @Test
  fun repositoryIsRebuiltAfterFactoryFailure() {
    var attempts = 0
    val provider = LocationUpdatesRepositoryProvider {
      if (attempts++ == 0) throw IllegalStateException("Firebase not initialized")
      fake
    }

    assertThrows(IllegalStateException::class.java) { provider.repository }
    assertSame(fake, provider.repository)
  }
}
