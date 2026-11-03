// Written by Neha Chakraborty, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.account

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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

  @Test
  fun aWriteThatFailsIsReported() {
    val editor =
        mockk<SharedPreferences.Editor> {
          every { putBoolean(any(), any()) } returns this@mockk
          every { remove(any()) } returns this@mockk
          every { commit() } returns false
        }
    val preferences = mockk<SharedPreferences> { every { edit() } returns editor }
    val fullDisk =
        mockk<Context> { every { getSharedPreferences(any(), any()) } returns preferences }
    val marker = SharedPreferencesDeletionMarker(fullDisk)

    assertThrows(IOException::class.java) { marker.set() }
    assertThrows(IOException::class.java) { marker.remove() }
  }
}
