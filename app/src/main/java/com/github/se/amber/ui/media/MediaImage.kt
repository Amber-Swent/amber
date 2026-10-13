// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.ui.media

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive

/** Test tags of [MediaImage]. */
object MediaImageTestTags {
  /** The picture, only once it is decoded and shown. */
  const val IMAGE = "mediaImage"
}

/** What TalkBack reads for a [MediaImage] given a blank description: never nothing. */
const val MEDIA_IMAGE_FALLBACK_TALKBACK = "Photo"

private const val TAG = "MediaImage"

/** Where [MediaImage] is in getting the local file of its media. */
private sealed interface FileState {
  data object Loading : FileState

  data class Ready(val file: File) : FileState

  data object Failed : FileState
}

/**
 * Shows the picture of a media from its local file.
 *
 * [loadFile] gives the file, usually `{ repository.getFile(circle, item) }` built by a ViewModel;
 * composables never call the repository themselves. It runs once per [mediaId], not on every
 * recomposition, and its latest value is used. Coil then only decodes and displays the file: it
 * never downloads, and it keeps decoded images in its memory cache only (the disk cache for media
 * is `MediaFileCache`).
 *
 * Give [modifier] a fixed size or aspect ratio (for example `item.width / item.height` of a
 * `MediaItem.Picture`), so the image keeps the same size while loading and the list doesn't jump
 * when it appears.
 *
 * A failure can be retried in two ways, which both load and decode the file again: [failed] gets a
 * retry action (for example for a "Try again" button), and a change of [retryKey] retries the image
 * if it is failed. [MediaImage] doesn't know why it should retry: a ViewModel decides it, for
 * example by changing [retryKey] when the connection comes back.
 *
 * Limitations: when the image appears, [loading] shows at least for its first frame, while
 * [loadFile] runs, even if the picture is already in Coil's memory cache. The slots are drawn
 * behind the picture, so for one frame [loading] may be composed under a cached picture.
 *
 * @param mediaId the id of the media; a new id loads a new file.
 * @param loadFile returns the local file of the media; it may throw (for example offline and not
 *   cached), which shows [failed].
 * @param contentDescription what the picture shows, read by TalkBack once it is shown; some users
 *   rely on it, for them it is the content of the screen. Pass the media's description, even if
 *   blank: a blank one is read as [MEDIA_IMAGE_FALLBACK_TALKBACK]. Pass `null` only on purpose, for
 *   a decorative image whose content TalkBack already reads elsewhere (for example a thumbnail next
 *   to its description).
 * @param modifier the modifier of the whole component, slots included.
 * @param contentScale how the picture fills the space given by [modifier].
 * @param retryKey a new value retries the image if it is failed; an image that is loading or shown
 *   is left as is. Only a change matters, not the value: usually a counter from the ViewModel's
 *   state, incremented when failed images should try again (for example back online). Never a value
 *   that changes on every recomposition, like `Any()`. `null` means no automatic retry.
 * @param loading shown, centered, while the file is loaded or decoded; it stays the same
 *   composition through both steps.
 * @param failed shown, centered, if the file can't be loaded or decoded, whatever the cause. It
 *   gets a `retry` action that loads and decodes the file again. Each time [failed] is shown, its
 *   `retry` is new and works; calling the same `retry` several times (for example a double tap)
 *   still starts a single new load.
 */
@Composable
fun MediaImage(
    mediaId: String,
    loadFile: suspend () -> File,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    retryKey: Any? = null,
    loading: @Composable () -> Unit = {},
    failed: @Composable (retry: () -> Unit) -> Unit = {},
) {
  val currentLoadFile by rememberUpdatedState(loadFile)
  // Each retry is a new attempt for the same mediaId.
  var attempt by remember(mediaId) { mutableIntStateOf(0) }
  // One state per attempt: the first frame with a new id (or a retry) already shows loading, never
  // the previous media, and a late result of a previous load only reaches that previous state.
  var fileState by remember(mediaId, attempt) { mutableStateOf<FileState>(FileState.Loading) }
  LaunchedEffect(mediaId, attempt) {
    fileState =
        try {
          FileState.Ready(currentLoadFile())
        } catch (e: CancellationException) {
          // This image was cancelled (left the screen or got a new mediaId): just stop.
          ensureActive()
          // Otherwise loadFile gave up by itself, for example after a withTimeout.
          logLoadFailure(mediaId, e)
          FileState.Failed
        } catch (e: Exception) {
          logLoadFailure(mediaId, e)
          FileState.Failed
        }
  }

  val file = (fileState as? FileState.Ready)?.file
  // Coil's state for the current file; Empty until Coil starts, which is after the first frame.
  var imageState by
      remember(file) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

  val isFailed = fileState is FileState.Failed || imageState is AsyncImagePainter.State.Error
  // Read when retryKey changes, so it must be the latest value, not the one of the first frame.
  val currentIsFailed by rememberUpdatedState(isFailed)

  // A retry starts one new attempt: calling it again, or a retry kept from an earlier attempt,
  // does nothing. The new attempt, if it fails, gives failed a new retry.
  val failedAttempt = attempt
  val retry = { if (attempt == failedAttempt) attempt++ }
  LaunchedEffect(mediaId, retryKey) { if (currentIsFailed) retry() }

  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    // One call site per slot, so the same loading slot stays from loading the file to decoding
    // it (an animated spinner doesn't restart). The slots come before the picture, so they are
    // drawn behind it: a picture in Coil's memory cache is drawn over loading in the frame where
    // Coil starts, while for any other file AsyncImage draws nothing and loading stays visible.
    when {
      isFailed -> failed(retry)
      imageState !is AsyncImagePainter.State.Success -> loading()
    }
    if (file != null) {
      // Until the picture is decoded, it is hidden from TalkBack and from tests.
      val shown = imageState is AsyncImagePainter.State.Success
      AsyncImage(
          model = file,
          contentDescription =
              if (shown) contentDescription?.ifBlank { MEDIA_IMAGE_FALLBACK_TALKBACK } else null,
          modifier =
              Modifier.fillMaxSize()
                  .then(if (shown) Modifier.testTag(MediaImageTestTags.IMAGE) else Modifier),
          contentScale = contentScale,
          onState = {
            imageState = it
            if (it is AsyncImagePainter.State.Error) {
              Log.w(TAG, "Couldn't decode media $mediaId", it.result.throwable)
            }
          },
      )
    }
  }
}

/**
 * Logs why the file of [mediaId] couldn't be loaded, so that logcat tells the causes apart; only
 * the id and the exception, never the media itself.
 */
private fun logLoadFailure(mediaId: String, e: Exception) {
  if (e is IllegalArgumentException) {
    // Not a network or disk problem: the caller asked for a media that isn't in the circle.
    Log.e(TAG, "Couldn't load media $mediaId: bug in its loader", e)
  } else {
    Log.w(TAG, "Couldn't load media $mediaId", e)
  }
}

/**
 * Removes every decoded image from Coil's memory cache, so that private photos don't stay in memory
 * after sign-out or after leaving a circle.
 *
 * It belongs to the app-level session cleanup run on sign-out and when leaving a circle, which
 * first clears the local media through `MediaRepository`, then calls this. Call it only from there,
 * not from a screen or a ViewModel (it needs a [Context]). That cleanup doesn't exist yet, so
 * nothing calls this function for now.
 *
 * It always clears the images of every circle, also when leaving a single one: Coil's cache keys
 * are file paths, so it can't clear one circle only.
 */
fun clearMediaImageMemoryCache(context: Context) {
  SingletonImageLoader.get(context).memoryCache?.clear()
}
