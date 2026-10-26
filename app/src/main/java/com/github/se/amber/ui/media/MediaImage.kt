// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.ui.media

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import coil3.compose.AsyncImagePainter
import coil3.compose.ConstraintsSizeResolver
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.compose.rememberConstraintsSizeResolver
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import com.github.se.amber.data.media.image.MediaPictureUnavailableException
import com.github.se.amber.data.media.image.mediaPictureKey
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaPicture
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Logcat tag of [MediaImage]; failures to get the file are logged by `MediaPictureFetcher`. */
internal const val MEDIA_IMAGE_TAG = "MediaImage"

/** Test tags of [MediaImage]. */
object MediaImageTestTags {
  /** The picture, only once it is decoded and shown. */
  const val IMAGE = "mediaImage"
  /** [MediaImageRetryButton], shown by default when the picture can't be shown. */
  const val RETRY_BUTTON = "mediaImageRetryButton"
}

/** What TalkBack reads for a [MediaImage] whose description is blank, so it is never silent. */
const val MEDIA_IMAGE_FALLBACK_TALKBACK = "Photo"

/** What TalkBack reads for [MediaImageRetryButton]. */
const val MEDIA_IMAGE_RETRY_TALKBACK = "Load the photo again"

/**
 * Shows [picture], a media of [circle], whether it is already sent or still waiting on this phone
 * to be sent.
 *
 * The screen only passes data, usually from its ViewModel's state: the app's image loader gets the
 * file from the media repository (through `MediaPictureFetcher`), so neither the ViewModel nor the
 * screen deals with files. A picture already decoded is shown from memory, without getting its file
 * again. It is decoded at the size it is laid out at, not at full resolution.
 *
 * Give [modifier] a fixed size or aspect ratio (for example [aspectRatioOf]), so the image keeps
 * the same size while loading and the list doesn't jump when it appears.
 *
 * If the picture can't be shown, [retryButton] is shown instead; its action loads the picture
 * again, whatever went wrong. A picture that failed because the phone was offline also loads again
 * by itself when the connection comes back ([LocalMediaRetrySignal]); one whose load was still
 * running then, and fails afterwards, waits for a tap or the next comeback. Either way, one failure
 * starts at most one new load, so a double tap doesn't download the file twice. The picture is also
 * loaded again when its circle gets a storage bucket, when it gets a new storage path, and when it
 * is composed again (scrolled back into a list, or its screen reopened).
 *
 * @param contentDescription what TalkBack reads once the picture is shown: for some users, it is
 *   the content of the screen. The picture's description by default, and
 *   [MEDIA_IMAGE_FALLBACK_TALKBACK] if blank. Pass `null` only for a decorative image whose content
 *   TalkBack already reads elsewhere (for example a thumbnail next to its description).
 * @param modifier the modifier of the whole component, slots included.
 * @param contentScale how the picture fills the space given by [modifier].
 * @param loading shown, centered, until the picture is shown, while its file is got and decoded; it
 *   stays the same composition throughout (an animated spinner doesn't restart). It is drawn behind
 *   the picture, so anything drawn over it hides it, including a smaller copy of the picture from
 *   memory shown while the full one loads: don't use it as an overlay.
 * @param retryButton shown, centered, when the picture can't be shown; it gets the action loading
 *   it again. [MediaImageRetryButton] by default; pass another one where it fits better (for
 *   example a big button on a full-screen picture).
 */
@Composable
fun MediaImage(
    circle: CareCircle,
    picture: MediaItem.Picture,
    modifier: Modifier = Modifier,
    contentDescription: String? = picture.description,
    contentScale: ContentScale = ContentScale.Crop,
    loading: @Composable () -> Unit = {},
    retryButton: @Composable (retry: () -> Unit) -> Unit = { MediaImageRetryButton(onClick = it) },
) {
  // A new painter whenever the circle's id or bucket, or the picture's id or storage path, changes:
  // the file to get then changes, and the first frame of a new media shows loading, never the
  // previous one. An edit of the circle's people or of the
  // picture's description doesn't load it again.
  key(circle.id, circle.storageBucket, picture.id, picture.storagePath) {
    val sizeResolver = rememberConstraintsSizeResolver()
    val painter = rememberMediaPainter(circle, picture, sizeResolver, contentScale)
    val state by painter.state.collectAsState()
    val retry = remember(painter) { Retry(painter) }

    val retrySignal = LocalMediaRetrySignal.current
    LaunchedEffect(retrySignal, retry) { retrySignal.collect { retry.afterConnectionFailure() } }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
      // One call site per slot, so the same loading slot stays from getting the file to decoding
      // it. The slots come before the picture, so they are drawn behind it: a picture in Coil's
      // memory cache is drawn over loading from its first frame. Until the picture is decoded, it
      // is hidden from TalkBack and from tests.
      val shown = state is AsyncImagePainter.State.Success
      when {
        state is AsyncImagePainter.State.Error -> retryButton(retry::afterAnyFailure)
        !shown -> loading()
      }
      Image(
          painter = painter,
          contentDescription =
              if (shown) contentDescription?.ifBlank { MEDIA_IMAGE_FALLBACK_TALKBACK } else null,
          modifier =
              Modifier.fillMaxSize()
                  .then(sizeResolver)
                  .then(if (shown) Modifier.testTag(MediaImageTestTags.IMAGE) else Modifier),
          contentScale = contentScale,
      )
    }
  }
}

/** The default retry button of [MediaImage]: a refresh icon calling [onClick]. */
@Composable
fun MediaImageRetryButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  IconButton(onClick = onClick, modifier = modifier.testTag(MediaImageTestTags.RETRY_BUTTON)) {
    Icon(Icons.Outlined.Refresh, contentDescription = MEDIA_IMAGE_RETRY_TALKBACK)
  }
}

/**
 * Emits each time the phone's connection comes back, so each [MediaImage] that failed because it
 * was offline loads again; pictures that failed for another reason don't. Provided once, at the
 * root of the app (`MainActivity`), from `ConnectivityMonitor.backOnline`: screens and ViewModels
 * have nothing to do. Without a provider (previews, tests) it never emits.
 */
val LocalMediaRetrySignal = staticCompositionLocalOf<Flow<Unit>> { emptyFlow() }

/**
 * Gives this element [picture]'s aspect ratio, so its space is reserved before it loads; square if
 * its size isn't known.
 */
fun Modifier.aspectRatioOf(picture: MediaItem.Picture): Modifier =
    aspectRatio(
        if (picture.width > 0 && picture.height > 0) picture.width.toFloat() / picture.height
        else 1f
    )

/**
 * The painter loading [picture], decoded at the size [sizeResolver] measures: at full resolution, a
 * camera photo would take tens of MB in every grid cell. It is built once; [MediaImage] builds a
 * new one when the file to get changes.
 */
@Composable
private fun rememberMediaPainter(
    circle: CareCircle,
    picture: MediaItem.Picture,
    sizeResolver: ConstraintsSizeResolver,
    contentScale: ContentScale,
): AsyncImagePainter {
  val context = LocalPlatformContext.current
  val request =
      remember(context, sizeResolver) {
        ImageRequest.Builder(context)
            .data(MediaPicture(circle, picture))
            .size(sizeResolver)
            // The picture has one memory entry: a smaller request reuses it, a bigger one (e.g.
            // full screen after a thumbnail) decodes it again and replaces it. Meanwhile the
            // smaller one is drawn rather than nothing.
            .placeholderMemoryCacheKey(MemoryCache.Key(mediaPictureKey(circle.id, picture.id)))
            .build()
      }
  return rememberAsyncImagePainter(
      request,
      contentScale = contentScale,
      onError = { logFailure(picture, it.result.throwable) },
  )
}

/** Logs why [picture] can't be shown, unless `MediaPictureFetcher` already did. */
private fun logFailure(picture: MediaItem.Picture, error: Throwable) {
  if (error !is MediaPictureUnavailableException) {
    Log.w(MEDIA_IMAGE_TAG, "Couldn't show media ${picture.id}", error)
  }
}

/**
 * Loads [painter]'s picture again after a failure, whether a tap or the connection coming back asks
 * for it.
 *
 * Coil's `restart()` starts the new load at once: the painter shows it loading before `restart()`
 * returns. So a second call (a double tap, or a tap and the connection coming back together) finds
 * no failure and does nothing. Even if Coil changed that, a second `restart()` cancels the load
 * started by the first: the file is never downloaded twice at the same time. A picture failing
 * again can be retried again.
 */
private class Retry(private val painter: AsyncImagePainter) {
  /** Loads the picture again, whatever made it fail: the user asked for it. */
  fun afterAnyFailure() = ifFailed { true }

  /**
   * Loads the picture again only if it failed because of the connection: once back online, it may
   * load now, while a picture that can't be decoded, for example, would only fail again.
   */
  fun afterConnectionFailure() = ifFailed {
    it is MediaPictureUnavailableException && it.isConnectionFailure
  }

  /** Loads the picture again if it failed and [shouldRetry] accepts the failure's error. */
  private fun ifFailed(shouldRetry: (Throwable) -> Boolean) {
    val failure = painter.state.value as? AsyncImagePainter.State.Error ?: return
    if (shouldRetry(failure.result.throwable)) painter.restart()
  }
}
