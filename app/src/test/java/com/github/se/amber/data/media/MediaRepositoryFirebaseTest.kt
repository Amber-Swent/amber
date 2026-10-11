// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Checks the file part of [MediaRepositoryFirebase]: a fake [MediaFileStorage] (no network) behind
 * a real [MediaFileCache] in a temporary folder.
 */
class MediaRepositoryFirebaseTest {

  @get:Rule val tmp = TemporaryFolder()

  /** Fake remote storage: records each download as "circleId:storagePath". */
  private class FakeFileStorage : MediaFileStorage {
    val calls = mutableListOf<String>()

    override suspend fun downloadToFile(
        circle: CareCircle,
        storagePath: String,
        destination: File,
    ) {
      calls += "${circle.id}:$storagePath"
      destination.writeText("content of $storagePath")
    }
  }

  private val fileStorage = FakeFileStorage()
  private val cacheDir by lazy { File(tmp.root, "media") }

  private val c1 = CareCircle(id = "c1", storageBucket = "bucket-c1")
  private val c2 = CareCircle(id = "c2", storageBucket = "bucket-c2")

  private fun picture(circleId: String, mediaId: String) =
      MediaItem.Picture(id = mediaId, storagePath = "careCircles/$circleId/media/$mediaId.jpg")

  private fun TestScope.newRepository() =
      MediaRepositoryFirebase(
          fileStorage,
          MediaFileCache(cacheDir, ioDispatcher = StandardTestDispatcher(testScheduler)),
      )

  private fun cachedFileNames() = cacheDir.listFiles().orEmpty().map { it.name }.sorted()

  @Test
  fun getFileDownloadsFromTheGivenCircle() = runTest {
    val repository = newRepository()

    val file = repository.getFile(c1, picture("c1", "m1"))

    assertEquals("content of careCircles/c1/media/m1.jpg", file.readText())
    assertEquals(listOf("c1:careCircles/c1/media/m1.jpg"), fileStorage.calls)
  }

  @Test
  fun getFileOfMediaFromAnotherCircleIsRejectedWithoutDownload() = runTest {
    val repository = newRepository()

    // c10's id starts like c1's: still another circle
    for (other in listOf("c2", "c10")) {
      val error = runCatching { repository.getFile(c1, picture(other, "m1")) }.exceptionOrNull()
      assertTrue("$other's media was accepted", error is IllegalArgumentException)
    }
    assertEquals(emptyList<String>(), fileStorage.calls)
  }

  @Test
  fun clearCachedMediaOfACircleKeepsOtherCircles() = runTest {
    val repository = newRepository()
    repository.getFile(c1, picture("c1", "m1"))
    repository.getFile(c2, picture("c2", "m1"))

    repository.clearCachedMedia("c1")

    assertEquals(listOf("careCircles_c2_media_m1.jpg"), cachedFileNames())
  }

  @Test
  fun clearCachedMediaDeletesEveryCircle() = runTest {
    val repository = newRepository()
    repository.getFile(c1, picture("c1", "m1"))
    repository.getFile(c2, picture("c2", "m1"))

    repository.clearCachedMedia()

    assertEquals(emptyList<String>(), cachedFileNames())
    repository.getFile(c1, picture("c1", "m1")) // downloaded again
    assertEquals(3, fileStorage.calls.size)
  }
}
