// Written by Neha Chakraborty, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.account

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharedPreferencesDeletionMarkerTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun theMarkerIsSharedByEveryInstanceUntilRemoved() {
    assertFalse(SharedPreferencesDeletionMarker(context).isSet)

    SharedPreferencesDeletionMarker(context).set()
    assertTrue(SharedPreferencesDeletionMarker(context).isSet)

    SharedPreferencesDeletionMarker(context).remove()
    assertFalse(SharedPreferencesDeletionMarker(context).isSet)
  }
}
