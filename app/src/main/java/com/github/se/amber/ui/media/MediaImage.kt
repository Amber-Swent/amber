// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.ui.media

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
 * Limitations: a failure stays shown as long as the same [mediaId] is displayed, even if the
 * connection returns; the file is loaded again only when the image leaves the screen and comes
 * back, or gets a new [mediaId]. When the image appears, [loading] shows at least for its first
 * frame, while [loadFile] runs, even if the picture is already in Coil's memory cache.
 *
 * @param mediaId the id of the media; a new id loads a new file.
 * @param loadFile returns the local file of the media; it may throw (for example offline and not
 *   cached), which shows [failed].
 * @param contentDescription what the picture shows, read by TalkBack once it is shown; give one
 *   whenever possible.
 * @param modifier the modifier of the whole component, slots included.
 * @param contentScale how the picture fills the space given by [modifier].
 * @param loading shown, centered, while the file is loaded or decoded.
 * @param failed shown, centered, if the file can't be loaded or decoded.
 */
@Composable
fun MediaImage(
    mediaId: String,
    loadFile: suspend () -> File,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    loading: @Composable () -> Unit = {},
    failed: @Composable () -> Unit = {},
) {
  val currentLoadFile by rememberUpdatedState(loadFile)
  // One state per mediaId: the first frame with a new id already shows loading, never the
  // previous media, and a late result of a previous load only reaches that previous state.
  var fileState by remember(mediaId) { mutableStateOf<FileState>(FileState.Loading) }
  LaunchedEffect(mediaId) {
    fileState =
        try {
          FileState.Ready(currentLoadFile())
        } catch (e: CancellationException) {
          // This image was cancelled (left the screen or got a new mediaId): just stop.
          ensureActive()
          // Otherwise loadFile gave up by itself, for example after a withTimeout.
          FileState.Failed
        } catch (e: Exception) {
          FileState.Failed
        }
  }

  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    when (val state = fileState) {
      FileState.Loading -> loading()
      FileState.Failed -> failed()
      is FileState.Ready -> {
        var imageState by
            remember(state.file) {
              mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
            }
        // Until the picture is decoded, it is hidden from TalkBack and from tests.
        val shown = imageState is AsyncImagePainter.State.Success
        AsyncImage(
            model = state.file,
            contentDescription = if (shown) contentDescription else null,
            modifier =
                Modifier.fillMaxSize()
                    .then(if (shown) Modifier.testTag(MediaImageTestTags.IMAGE) else Modifier),
            contentScale = contentScale,
            onState = { imageState = it },
        )
        // AsyncImage draws nothing while it decodes the file or if decoding fails, so the slots
        // cover those cases too. Not while Empty: that is only the frame before Coil starts, and
        // a picture in Coil's memory cache goes straight to Success.
        when (imageState) {
          is AsyncImagePainter.State.Loading -> loading()
          is AsyncImagePainter.State.Error -> failed()
          else -> Unit
        }
      }
    }
  }
}

/**
 * Removes every decoded image from Coil's memory cache, so that private photos don't stay in memory
 * after sign-out or after leaving a circle. Call it once, next to `MediaRepository`'s clearing of
 * local media, not from each screen.
 */
fun clearMediaImageMemoryCache(context: Context) {
  SingletonImageLoader.get(context).memoryCache?.clear()
}
