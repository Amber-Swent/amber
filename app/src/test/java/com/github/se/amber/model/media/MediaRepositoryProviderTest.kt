// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.media

import android.content.Context
import com.github.se.amber.data.media.MediaFileStorageFirebase
import com.github.se.amber.model.circle.CareCircle
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Checks how [MediaRepositoryProvider] wires the media layer, on Robolectric. Nothing here contacts
 * Firebase: creating the instances needs no network.
 */
@RunWith(RobolectricTestRunner::class)
class MediaRepositoryProviderTest {

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
  fun everyCallReturnsTheSameFileStorageAndCache() {
    val cache = MediaRepositoryProvider.mediaFileCache(context)

    assertTrue(MediaRepositoryProvider.fileStorage is MediaFileStorageFirebase)
    assertSame(MediaRepositoryProvider.fileStorage, MediaRepositoryProvider.fileStorage)
    // the cache must be the only one using its folder, whatever context is passed
    assertSame(cache, MediaRepositoryProvider.mediaFileCache(context.applicationContext))
  }

  @Test
  fun cacheStoresFilesOfEveryCircleInTheMediaFolder() = runTest {
    val cache = MediaRepositoryProvider.mediaFileCache(context)

    cache.put("careCircles/c1/media/m1.jpg", tempFile("first circle"))
    cache.put("careCircles/c2/media/m1.jpg", tempFile("second circle"))

    val folder = File(context.cacheDir, "media")
    assertEquals("first circle", File(folder, "careCircles_c1_media_m1.jpg").readText())
    assertEquals("second circle", File(folder, "careCircles_c2_media_m1.jpg").readText())
    cache.clear()
  }

  @Test
  fun fileStorageResolvesTheCircleBucketThroughCircleStorageProvider() = runTest {
    val destination = tempFile("")

    // CircleStorageProvider refuses a circle whose bucket isn't assigned, before any network call
    val error = runCatching {
      MediaRepositoryProvider.fileStorage.downloadToFile(
          CareCircle(id = "pending"),
          "careCircles/pending/media/m1.jpg",
          destination,
      )
    }
        .exceptionOrNull()

    assertTrue(error is IllegalArgumentException)
    assertEquals("Storage is not ready for care circle pending", error?.message)
  }

  private fun tempFile(text: String) =
      File.createTempFile("media", ".jpg").apply { writeText(text) }
}
