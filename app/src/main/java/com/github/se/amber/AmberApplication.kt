// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.github.se.amber.model.media.MediaRepositoryProvider
import com.github.se.amber.ui.media.MediaPictureFetcher
import com.github.se.amber.ui.media.MediaPictureKeyer

/**
 * The app itself, created by Android before any activity.
 *
 * It sets up the app's image loader, so that `MediaImage` gets its pictures from the media
 * repository and keeps them decoded under their media id. Coil builds it the first time an image is
 * shown, so nothing is built at startup. It has no network fetcher: Coil never downloads anything
 * by itself, the repository does.
 */
class AmberApplication : Application(), SingletonImageLoader.Factory {

  override fun newImageLoader(context: PlatformContext): ImageLoader =
      ImageLoader.Builder(context)
          .components {
            add(MediaPictureKeyer())
            add(MediaPictureFetcher.Factory { MediaRepositoryProvider.repository })
          }
          .build()
}
