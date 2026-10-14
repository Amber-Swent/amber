// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.ui.media

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import coil3.compose.AsyncImagePainter
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

/** Logcat tag of [MediaImage]; failures to get the file are logged by `MediaPictureFetcher`. */
internal const val MEDIA_IMAGE_TAG = "MediaImage"

/** Test tags of [MediaImage]. */
object MediaImageTestTags {
  /** The picture, only once it is decoded and shown. */
  const val IMAGE = "mediaImage"
}

/** What TalkBack reads for a [MediaImage] whose description is blank, so it is never silent. */
const val MEDIA_IMAGE_FALLBACK_TALKBACK = "Photo"

/**
 * Shows [picture], a media of [circle], whether it is sent or still in the outbox of this phone.
 *
 * The screen only passes data, usually from its ViewModel's state: the app's image loader gets the
 * file from the repository (`MediaPictureFetcher`), so no ViewModel builds a loader and composables
 * never call the repository. A picture already decoded is shown from memory without touching the
 * repository, and stays there after it is sent. It is decoded at the size it is laid out at.
 *
 * Give [modifier] a fixed size or aspect ratio (for example [aspectRatioOf]), so the image keeps
 * the same size while loading and the list doesn't jump when it appears.
 *
 * @param contentDescription what TalkBack reads once the picture is shown: for some users, it is
 *   the content of the screen. The picture's description by default, and
 *   [MEDIA_IMAGE_FALLBACK_TALKBACK] if blank. Pass `null` only for a decorative image whose content
 *   TalkBack already reads elsewhere (for example a thumbnail next to its description).
 * @param modifier the modifier of the whole component, slots included.
 * @param contentScale how the picture fills the space given by [modifier].
 * @param loading shown, centered, until the picture is shown, while its file is got and decoded; it
 *   stays the same composition throughout. It is drawn behind the picture, so it is hidden as soon
 *   as a picture is drawn (also a smaller copy from memory, see below): don't use it as an overlay.
 * @param failed shown, centered, if the file can't be got or decoded, whatever the cause. It gets a
 *   `retry` action that loads the picture again; calling it several times (for example a double
 *   tap) still starts a single new load. A failed picture isn't retried by itself when the
 *   connection comes back: it loads again only through `retry`, when its circle gets a bucket or it
 *   gets a new storage path, or when it is composed again (scrolled back into a list, or its screen
 *   reopened).
 */
@Composable
fun MediaImage(
    circle: CareCircle,
    picture: MediaItem.Picture,
    modifier: Modifier = Modifier,
    contentDescription: String? = picture.description,
    contentScale: ContentScale = ContentScale.Crop,
    loading: @Composable () -> Unit = {},
    failed: @Composable (retry: () -> Unit) -> Unit = {},
) {
  // A new painter whenever what getting the file depends on changes, so the first frame of a new
  // media shows loading, never the previous one. An edit of the circle's people or of the
  // picture's description doesn't load it again.
  key(circle.id, circle.storageBucket, picture.id, picture.storagePath) {
    val model = remember { MediaPicture(circle, picture) }
    val context = LocalPlatformContext.current
    // Decodes the picture at the size it is laid out at, not at its full resolution: a camera
    // photo would otherwise take tens of MB in every grid cell.
    val sizeResolver = rememberConstraintsSizeResolver()
    val request =
        remember(context, sizeResolver) {
          ImageRequest.Builder(context)
              .data(model)
              .size(sizeResolver)
              // The picture has one memory entry: a smaller request reuses it, a bigger one (e.g.
              // full screen after a thumbnail) decodes it again and replaces it. Meanwhile the
              // smaller one is drawn rather than nothing.
              .placeholderMemoryCacheKey(MemoryCache.Key(mediaPictureKey(circle.id, picture.id)))
              .build()
        }
    val painter =
        rememberAsyncImagePainter(
            request,
            contentScale = contentScale,
            onError = {
              // failures to get the file are logged by MediaPictureFetcher
              val error = it.result.throwable
              if (error !is MediaPictureUnavailableException) {
                Log.w(MEDIA_IMAGE_TAG, "Couldn't show media ${picture.id}", error)
              }
            },
        )
    val state by painter.state.collectAsState()

    // The failure a retry was already started for: a second call does nothing until the new load
    // fails in turn. Coil already shows loading as soon as it restarts; this doesn't depend on it.
    var retriedFailure by remember { mutableStateOf<AsyncImagePainter.State.Error?>(null) }
    val retry = {
      val current = painter.state.value
      if (current is AsyncImagePainter.State.Error && current !== retriedFailure) {
        retriedFailure = current
        painter.restart()
      }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
      // One call site per slot, so the same loading slot stays from getting the file to decoding
      // it (an animated spinner doesn't restart). The slots come before the picture, so they are
      // drawn behind it: a picture in Coil's memory cache is drawn over loading from its first
      // frame. Until the picture is decoded, it is hidden from TalkBack and from tests.
      val shown = state is AsyncImagePainter.State.Success
      when {
        state is AsyncImagePainter.State.Error -> failed(retry)
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

/**
 * Gives this element [picture]'s aspect ratio, so its space is reserved before it loads; square if
 * its size isn't known.
 */
fun Modifier.aspectRatioOf(picture: MediaItem.Picture): Modifier =
    aspectRatio(
        if (picture.width > 0 && picture.height > 0) picture.width.toFloat() / picture.height
        else 1f
    )
