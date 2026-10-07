// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.media

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Firebase
import com.google.firebase.storage.storage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks [MediaStorageRepositoryProvider] in the real app process, where Firebase is initialized.
 * Nothing here contacts Firebase: creating the instances needs no network.
 */
@RunWith(AndroidJUnit4::class)
class MediaStorageRepositoryProviderTest {

  private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

  @Test
  fun everyCallReturnsTheSameRepositoryAndCache() {
    val repository = MediaStorageRepositoryProvider.repository
    val cache = MediaStorageRepositoryProvider.mediaFileCache(context)

    assertTrue(repository is MediaStorageRepositoryFirebase)
    // second calls must not build new instances: the cache must be the only one using its folder
    assertSame(repository, MediaStorageRepositoryProvider.repository)
    assertSame(cache, MediaStorageRepositoryProvider.mediaFileCache(context.applicationContext))
  }

  @Test
  fun downloadsStopRetryingAfterFiveSeconds() {
    MediaStorageRepositoryProvider.repository // sets the limit on first creation

    assertEquals(5_000L, Firebase.storage.maxDownloadRetryTimeMillis)
  }
}
