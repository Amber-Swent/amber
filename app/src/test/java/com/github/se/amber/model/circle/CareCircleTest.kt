// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.circle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CareCircleTest {

  @Test
  fun aCircleHasAStorageBucketOnlyOnceOneIsAssigned() {
    assertFalse(CareCircle(id = "c1").hasStorageBucket)
    assertFalse(CareCircle(id = "c1", storageBucket = "  ").hasStorageBucket)
    assertTrue(CareCircle(id = "c1", storageBucket = "bucket-c1").hasStorageBucket)
  }
}
