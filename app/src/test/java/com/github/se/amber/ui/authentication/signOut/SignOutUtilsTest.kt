// Written by Alaabrine, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Alaabrine, with assistance from Cursor (Grok).
// Modified by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.authentication.signOut

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test

class SignOutUtilsTest {
  @Test
  fun previewActiveCircleRepositoryIgnoresReadsAndWrites() = runTest {
    val circles = listOf("circle-a", "circle-b")

    assertNull(PreviewActiveCircleRepository.activeCircleId("caregiver", circles).first())

    PreviewActiveCircleRepository.setActiveCircle("caregiver", "circle-b")
    PreviewActiveCircleRepository.clear("caregiver")

    assertNull(PreviewActiveCircleRepository.activeCircleId("caregiver", circles).first())
  }
}
