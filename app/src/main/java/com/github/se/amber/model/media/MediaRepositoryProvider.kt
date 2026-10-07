// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.media

import android.content.Context
import com.github.se.amber.data.media.MediaFileCache
import com.github.se.amber.data.media.MediaFileStorage
import com.github.se.amber.data.media.MediaFileStorageFirebase
import java.io.File

/**
 * The one place where the media layer is built. ViewModel factories will get the media repository
 * from here, and nothing else: [MediaFileStorage] and [MediaFileCache] are its building blocks.
 *
 * TODO: once the media repository exists, build it here from [fileStorage] and [mediaFileCache],
 *   expose only it, and make those two private.
 *
 * Being an `object`, it exists once, so every caller shares the same instances; this matters for
 * the cache, which must be the only one using its folder. Code that needs them should receive them
 * as constructor parameters rather than read this object itself, so tests can pass fakes instead.
 */
object MediaRepositoryProvider {

  /** The Firebase-backed file storage, which reads each circle's own bucket. */
  val fileStorage: MediaFileStorage by lazy { MediaFileStorageFirebase() }

  @Volatile private var cache: MediaFileCache? = null

  /**
   * The cache of downloaded media of every circle, in a `media` folder of the app's cache
   * directory. Created on the first call; later calls return the same instance, whatever [context]
   * they pass.
   */
  fun mediaFileCache(context: Context): MediaFileCache =
      // checked again inside synchronized: two first calls at once must not both create one
      cache
          ?: synchronized(this) {
            cache
                ?: MediaFileCache(File(context.applicationContext.cacheDir, CACHE_DIR)).also {
                  cache = it
                }
          }

  private const val CACHE_DIR = "media"
}
