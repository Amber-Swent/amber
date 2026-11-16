// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver.upload.audios

import android.Manifest
import android.app.Activity
import android.app.Application
import androidx.core.app.ActivityCompat
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests the microphone helpers of [UploadAudioUtils]: remembering that Android was asked,
 * forgetting it, and telling whether Android would still show its popup. The rationale of Android
 * is mocked, so nothing needs a real permission popup.
 */
@RunWith(RobolectricTestRunner::class)
class UploadAudioUtilsTest {

  private lateinit var context: Application
  private val activity: Activity = mockk()

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    mockkStatic(ActivityCompat::class)
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  private fun rationale(shown: Boolean) {
    every {
      ActivityCompat.shouldShowRequestPermissionRationale(
          activity,
          Manifest.permission.RECORD_AUDIO,
      )
    } returns shown
  }

  // ---------- hasAskedMicrophone, markMicrophoneAsked, clearMicrophoneAsked ----------

  @Test
  fun androidWasNotAskedAtFirst() {
    assertFalse(context.hasAskedMicrophone())
  }

  @Test
  fun markingRemembersThatAndroidWasAsked() {
    context.markMicrophoneAsked()
    assertTrue(context.hasAskedMicrophone())
  }

  @Test
  fun clearingForgetsThatAndroidWasAsked() {
    context.markMicrophoneAsked()
    context.clearMicrophoneAsked()
    assertFalse(context.hasAskedMicrophone())
  }

  @Test
  fun clearingWhenAndroidWasNeverAskedKeepsItForgotten() {
    context.clearMicrophoneAsked()
    assertFalse(context.hasAskedMicrophone())
  }

  // ---------- androidWillAskMicrophone ----------

  @Test
  fun androidWillAskWhenItWasNeverAsked() {
    rationale(shown = false)
    assertTrue(context.androidWillAskMicrophone(activity))
  }

  @Test
  fun androidWillAskAfterOneRefusal() {
    context.markMicrophoneAsked()
    rationale(shown = true)
    assertTrue(context.androidWillAskMicrophone(activity))
  }

  @Test
  fun androidWillNotAskAfterTwoRefusals() {
    context.markMicrophoneAsked()
    rationale(shown = false)
    assertFalse(context.androidWillAskMicrophone(activity))
  }

  @Test
  fun androidWillAskAgainAfterClearingStoredRequestHistory() {
    context.markMicrophoneAsked()
    context.clearMicrophoneAsked()
    rationale(shown = false)
    assertTrue(context.androidWillAskMicrophone(activity))
  }

  @Test
  fun withoutActivityAndroidIsSupposedToAsk() {
    context.markMicrophoneAsked()
    assertTrue(context.androidWillAskMicrophone(null))
  }
}
