// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber

import android.app.Application
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.github.se.amber.data.media.image.MediaPictureFetcher
import com.github.se.amber.data.media.image.MediaPictureKeyer
import com.github.se.amber.model.media.MediaRepositoryProvider

/**
 * The app itself, created by Android before any activity or background job.
 *
 * It sets up two libraries, each the first time it is used, so nothing is built at startup:
 * - the app's image loader, so that `MediaImage` gets its pictures from the media repository and
 *   keeps them decoded under their media id. It has no network fetcher: Coil never downloads
 *   anything by itself, the repository does;
 * - WorkManager, so that background jobs are created by [MediaRepositoryProvider.workerFactory] and
 *   get what they need. Its automatic setup is turned off in the manifest, so it reads
 *   [workManagerConfiguration] instead.
 */
class AmberApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {

  override fun newImageLoader(context: PlatformContext): ImageLoader =
      ImageLoader.Builder(context)
          .components {
            add(MediaPictureKeyer())
            add(MediaPictureFetcher.Factory { MediaRepositoryProvider.repository })
          }
          // The media repository's cache is the only place media files are kept on disk: they are
          // private photos, so Coil must never keep its own copy. Our fetcher doesn't write to
          // Coil's disk cache anyway; this keeps it that way if another fetcher is added later.
          .diskCache(null)
          .build()

  override val workManagerConfiguration: Configuration by lazy {
    Configuration.Builder().setWorkerFactory(MediaRepositoryProvider.workerFactory).build()
  }
}
