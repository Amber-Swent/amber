// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.directions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class RouteRepositoryProviderTest {

  private val fake = RouteRepository { _, _ -> Result.failure(RouteError.NoRoute) }

  @Test
  fun repositoryIsBuiltOnlyOnceByFactory() {
    var builds = 0
    val provider = RouteRepositoryProvider {
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
    val provider = RouteRepositoryProvider {
      if (attempts++ == 0) throw IllegalStateException("Firebase not initialized")
      fake
    }

    assertThrows(IllegalStateException::class.java) { provider.repository }
    assertSame(fake, provider.repository)
  }
}
