// Written by Neha Chakraborty, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.account

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AccountDeletionRepositoryProviderTest {
  private val fake: AccountDeletionRepository = mockk()

  @Test
  fun repositoryIsBuiltOnceByFactoryAndAgainAfterAFailure() {
    var attempts = 0
    val provider = AccountDeletionRepositoryProvider {
      if (attempts++ == 0) throw IllegalStateException("Firebase not initialized")
      fake
    }

    runCatching { provider.repository }

    assertSame(fake, provider.repository)
    assertSame(fake, provider.repository)
    assertEquals(2, attempts)
  }
}
