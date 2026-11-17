// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.location

import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class MediaLocationRepositoryProviderTest {

  private val fake = MediaLocationRepository { emptyFlow() }

  @Test
  fun repositoryIsBuiltOnlyOnceByFactory() {
    var builds = 0
    val provider = MediaLocationRepositoryProvider {
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
    val provider = MediaLocationRepositoryProvider {
      if (attempts++ == 0) throw IllegalStateException("Firebase not initialized")
      fake
    }

    assertThrows(IllegalStateException::class.java) { provider.repository }
    assertSame(fake, provider.repository)
  }
}
