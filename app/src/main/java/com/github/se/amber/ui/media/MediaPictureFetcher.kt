// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.ui.media

import android.util.Log
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.github.se.amber.model.media.MediaRepository
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.Source
import okio.buffer
import okio.source

/**
 * Gives Coil the bytes of a [MediaPicture]: gets its file with [MediaRepository.getFile], then
 * opens it at once, as getFile asks. On Android, an open file stays readable even if it is then
 * moved (its media is sent) or deleted (evicted from the cache), so Coil can decode it whatever
 * happens to it.
 *
 * If the file is gone between getFile and opening it (a send moved it, or an eviction deleted it,
 * at that moment), getFile is called once more and finds it where it is now.
 *
 * Any failure reaches Coil as a [MediaPictureUnavailableException], so [MediaImage] shows its
 * `failed` slot: also a [CancellationException] thrown by getFile itself (e.g. a timeout) while
 * this load wasn't cancelled, which Coil would otherwise take for a cancellation and leave the
 * image loading forever. Each failure is logged, with the media id only, never the media: as an
 * error for a picture shown with the wrong circle (a bug), as a warning otherwise. A load that was
 * cancelled just stops, without failing or logging, whatever getFile threw.
 */
class MediaPictureFetcher(
    private val data: MediaPicture,
    private val repository: MediaRepository,
    private val options: Options,
) : Fetcher {

  override suspend fun fetch(): FetchResult {
    val source =
        try {
          openFile()
        } catch (e: Exception) {
          // This load was cancelled (the image left the screen), whatever getFile threw then: just
          // stop, it isn't a failure.
          currentCoroutineContext().ensureActive()
          throw unavailable(e)
        }
    return SourceFetchResult(
        source = ImageSource(source.buffer(), options.fileSystem),
        mimeType = null, // the decoder reads it from the bytes
        dataSource = DataSource.DISK,
    )
  }

  private suspend fun openFile(): Source {
    val file = repository.getFile(data.circle, data.picture)
    return try {
      file.source()
    } catch (gone: FileNotFoundException) {
      try {
        repository.getFile(data.circle, data.picture).source()
      } catch (e: Exception) {
        // kept with the second failure, so the log shows both; a cancellation is left as it is
        if (e !is CancellationException) e.addSuppressed(gone)
        throw e
      }
    }
  }

  private fun unavailable(e: Exception): MediaPictureUnavailableException {
    val mediaId = data.picture.id
    if (e is IllegalArgumentException && data.circle.storageBucket.isBlank()) {
      // a normal state: the circle is still being set up, and its file had to be downloaded
      Log.w(MEDIA_IMAGE_TAG, "Couldn't load media $mediaId: its circle has no bucket yet", e)
    } else if (e is IllegalArgumentException) {
      // not a network or disk problem: a bug, the picture isn't a media of this circle
      Log.e(MEDIA_IMAGE_TAG, "Couldn't load media $mediaId: wrong circle", e)
    } else {
      Log.w(MEDIA_IMAGE_TAG, "Couldn't load media $mediaId", e)
    }
    return MediaPictureUnavailableException(mediaId, e)
  }

  /**
   * Creates the [MediaPictureFetcher]s of the app's image loader. [repository] is called only when
   * a picture is loaded, so building the image loader doesn't build the repository.
   */
  class Factory(private val repository: () -> MediaRepository) : Fetcher.Factory<MediaPicture> {
    override fun create(data: MediaPicture, options: Options, imageLoader: ImageLoader): Fetcher =
        MediaPictureFetcher(data, repository(), options)
  }
}

/**
 * The file of media [mediaId] couldn't be got or opened; [cause] says why. Already logged by
 * [MediaPictureFetcher].
 */
class MediaPictureUnavailableException(mediaId: String, cause: Throwable) :
    IOException("Couldn't load media $mediaId", cause)
