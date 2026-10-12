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
 * frame, while [loadFile] runs, even if the picture is already in Coil's memory cache. The slots
 * are drawn behind the picture, so for one frame [loading] may be composed under a cached picture.
 *
 * @param mediaId the id of the media; a new id loads a new file.
 * @param loadFile returns the local file of the media; it may throw (for example offline and not
 *   cached), which shows [failed].
 * @param contentDescription what the picture shows, read by TalkBack once it is shown; give one
 *   whenever possible.
 * @param modifier the modifier of the whole component, slots included.
 * @param contentScale how the picture fills the space given by [modifier].
 * @param loading shown, centered, while the file is loaded or decoded; it stays the same
 *   composition through both steps.
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

  val file = (fileState as? FileState.Ready)?.file
  // Coil's state for the current file; Empty until Coil starts, which is after the first frame.
  var imageState by
      remember(file) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    // One call site per slot, so the same loading slot stays from loading the file to decoding
    // it (an animated spinner doesn't restart). The slots come before the picture, so they are
    // drawn behind it: a picture in Coil's memory cache is drawn over loading in the frame where
    // Coil starts, while for any other file AsyncImage draws nothing and loading stays visible.
    when {
      fileState is FileState.Failed || imageState is AsyncImagePainter.State.Error -> failed()
      imageState !is AsyncImagePainter.State.Success -> loading()
    }
    if (file != null) {
      // Until the picture is decoded, it is hidden from TalkBack and from tests.
      val shown = imageState is AsyncImagePainter.State.Success
      AsyncImage(
          model = file,
          contentDescription = if (shown) contentDescription else null,
          modifier =
              Modifier.fillMaxSize()
                  .then(if (shown) Modifier.testTag(MediaImageTestTags.IMAGE) else Modifier),
          contentScale = contentScale,
          onState = { imageState = it },
      )
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
