// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.media.audio

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Checks how [SelectedAudioReaderProvider] builds its instance, on Robolectric, where Firebase
 * (which gives it the app's context) is initialized by hand like the app does at startup. Nothing
 * here contacts Firebase.
 */
@RunWith(RobolectricTestRunner::class)
class SelectedAudioReaderProviderTest {

  private val context: Context = RuntimeEnvironment.getApplication()

  @Before
  fun initializeFirebase() {
    if (FirebaseApp.getApps(context).isEmpty()) {
      FirebaseApp.initializeApp(
          context,
          FirebaseOptions.Builder()
              .setApplicationId("1:123456789012:android:abcdef123456")
              .setApiKey("test-api-key")
              .setProjectId("amber-test")
              .build(),
      )
    }
  }

  @Test
  fun readerIsASingleAndroidInstance() {
    val reader = SelectedAudioReaderProvider.reader

    assertTrue(reader is AndroidSelectedAudioReader)
    assertSame(reader, SelectedAudioReaderProvider.reader)
  }
}
