// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.media

import android.content.Context
import com.github.se.amber.data.media.MediaRepositoryFirebase
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
 * Checks how [MediaRepositoryProvider] builds the repository, on Robolectric, where Firebase is
 * initialized by hand like the app does at startup. Nothing here contacts Firebase.
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
  fun repositoryIsASingleFirebaseInstance() {
    val repository = MediaRepositoryProvider.repository

    assertTrue(repository is MediaRepositoryFirebase)
    // its cache must be the only one using its folder
    assertSame(repository, MediaRepositoryProvider.repository)
  }

  @Test
  fun filesAreCachedInTheMediaFolderOfTheAppCache() = runTest {
    // the app's context as the provider gets it: Robolectric gives each test a new application,
    // but Firebase, like the repository, keeps the first one
    val appCacheDir = FirebaseApp.getInstance().applicationContext.cacheDir
    val cached = File(appCacheDir, "media/careCircles_cached_media_m1.jpg")
    cached.parentFile!!.mkdirs()
    cached.writeText("cached before")
    // no bucket: a cache miss would fail at once instead of trying the network
    val circle = CareCircle(id = "cached")
    val item = MediaItem.Picture(id = "m1", storagePath = "careCircles/cached/media/m1.jpg")

    // a cache hit: no download
    val file = MediaRepositoryProvider.repository.getFile(circle, item)

    assertEquals("cached before", file.readText())
    MediaRepositoryProvider.repository.clearCachedMedia("cached")
  }
}
