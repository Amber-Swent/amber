// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.image

import android.util.Log
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.github.se.amber.model.circle.hasStorageBucket
import com.github.se.amber.model.media.MediaPicture
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.isMediaOf
import com.google.firebase.storage.StorageException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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
 * Any failure reaches Coil as a [MediaPictureUnavailableException], so `MediaImage` shows its retry
 * button: also a [CancellationException] thrown by getFile itself (for example a repository giving
 * up with a timeout) while this load wasn't cancelled, which Coil would otherwise take for a
 * cancellation and leave the image loading forever. The exception says whether the connection
 * caused it, so `MediaImage` knows to load the picture again once the phone is back online. Each
 * failure is logged under [MEDIA_PICTURE_FETCHER_TAG] with its [MediaPictureFailure], and the media
 * id only, never the media: as an error for a bug, as a warning otherwise. A load that was
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
    val failure = failureOf(e)
    val message = "Couldn't load media $mediaId: ${failure.reason}"
    if (failure.logAsError) {
      Log.e(MEDIA_PICTURE_FETCHER_TAG, message, e)
    } else {
      Log.w(MEDIA_PICTURE_FETCHER_TAG, message, e)
    }
    return MediaPictureUnavailableException(
        mediaId,
        cause = e,
        isConnectionFailure = failure == MediaPictureFailure.CONNECTION,
    )
  }

  /** Which [MediaPictureFailure] [e] is: the first group that matches, from top to bottom. */
  private fun failureOf(e: Exception): MediaPictureFailure =
      when {
        // a bug, checked first: a missing bucket must not hide it
        e is IllegalArgumentException && !data.picture.isMediaOf(data.circle.id) ->
            MediaPictureFailure.WRONG_CIRCLE
        e is IllegalArgumentException && !data.circle.hasStorageBucket ->
            MediaPictureFailure.NO_BUCKET_YET
        e is IllegalArgumentException -> MediaPictureFailure.INVALID_MEDIA
        e.causedByConnection() -> MediaPictureFailure.CONNECTION
        else -> MediaPictureFailure.OTHER
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

/** Logcat tag of [MediaPictureFetcher]. */
internal const val MEDIA_PICTURE_FETCHER_TAG = "MediaPictureFetcher"

/**
 * The file of media [mediaId] couldn't be got or opened; [cause] says why. Already logged by
 * [MediaPictureFetcher].
 *
 * @property isConnectionFailure whether the connection made it fail (typically: offline, and the
 *   file isn't on the phone), so it may load once the phone is back online. Lets the UI tell this
 *   apart without knowing Firebase.
 */
class MediaPictureUnavailableException(
    mediaId: String,
    cause: Throwable,
    val isConnectionFailure: Boolean,
) : IOException("Couldn't load media $mediaId", cause)

/**
 * Why a picture couldn't be loaded: decides how [MediaPictureFetcher] logs it, and whether
 * `MediaImage` loads it again once back online. What getFile can throw, by group:
 * - [WRONG_CIRCLE], [NO_BUCKET_YET], [INVALID_MEDIA]: an [IllegalArgumentException] from the
 *   repository, the cache or the storage of the circle.
 * - [CONNECTION]: a Firebase Storage download that got no answer at all ([StorageException] with
 *   code `ERROR_RETRY_LIMIT_EXCEEDED`: offline, unknown host, reset or refused connection), or one
 *   cut while the file was being read (another code, caused by a platform network error).
 * - [OTHER]: the rest, which coming back online doesn't fix. From Firebase Storage: a missing file
 *   (404), a denied access (401, 403), a server error even after Firebase's retries (5xx, 429,
 *   408), a full disk while writing the file. From the cache: no space for a new file, a move that
 *   failed, a sign-out clearing it meanwhile. Also a file still missing when opened again.
 *
 * A load that was cancelled isn't a failure, and doesn't get here.
 *
 * @property reason why it failed, in the logged message.
 * @property logAsError whether it is a bug in the app, which only a code change fixes: logged as an
 *   error so it stands out. Everything else is a warning, including failures that can't be sorted:
 *   they usually come from the phone or the server, and are often a normal state.
 */
private enum class MediaPictureFailure(val reason: String, val logAsError: Boolean) {
  /** The picture isn't a media of the circle it is shown with. */
  WRONG_CIRCLE("wrong circle", logAsError = true),
  /** A normal state: the circle is still being set up, and the file had to be downloaded. */
  NO_BUCKET_YET("its circle has no bucket yet", logAsError = false),
  /** Any other invalid argument: not a network or disk problem. */
  INVALID_MEDIA("invalid media", logAsError = true),
  /** See [causedByConnection]. */
  CONNECTION("connection problem", logAsError = false),
  /** Anything else: see the logged error. */
  OTHER("see the cause", logAsError = false),
}

/** Whether this error, or one of its causes, is a [connection error][isConnectionError]. */
private fun Throwable.causedByConnection(): Boolean =
    generateSequence(this) { it.cause }.any { it.isConnectionError() }

/** Whether this error itself, without its causes, comes from the connection. */
private fun Throwable.isConnectionError(): Boolean =
    when (this) {
      // Firebase Storage gave up retrying a request that got no HTTP answer at all. A request that
      // got one (404, 503…) has another code, even after Firebase's retries.
      is StorageException -> isRecoverableException
      // The platform's network errors. Firebase gives them as the cause of its StorageException,
      // also when the connection is cut while the file is being read, with another code.
      is UnknownHostException,
      is SocketException,
      is SocketTimeoutException -> true
      else -> false
    }
