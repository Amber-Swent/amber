// Written by GitHub Copilot.
package com.github.se.amber.data.storage

import com.github.se.amber.model.circle.CareCircle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CircleStorageProviderTest {
  @Test
  fun forCircleRejectsMissingStorageBucket() {
    val error =
        assertThrows(IllegalArgumentException::class.java) {
          CircleStorageProvider.forCircle(CareCircle(id = "circle-1"))
        }

    assertEquals("Storage is not ready for care circle circle-1", error.message)
  }

  @Test
  fun forCircleRejectsWhitespaceOnlyStorageBucket() {
    val error =
        assertThrows(IllegalArgumentException::class.java) {
          CircleStorageProvider.forCircle(CareCircle(id = "circle-1", storageBucket = "  "))
        }

    assertEquals("Storage is not ready for care circle circle-1", error.message)
  }
}
