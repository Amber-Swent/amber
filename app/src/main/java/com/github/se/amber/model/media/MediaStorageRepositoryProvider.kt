// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.media

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.storage.storage
import java.io.File

/**
 * Holds the app's single [MediaStorageRepository] and single [MediaFileCache]. Being an `object`,
 * it exists once, so every caller shares the same instances; this matters for the cache, which must
 * be the only one using its folder.
 *
 * Both are created on first use. Code that needs them takes them as constructor parameters
 * (defaulting to these), so tests can pass fakes instead.
 */
object MediaStorageRepositoryProvider {

  /** The Firebase-backed repository. */
  val repository: MediaStorageRepository by lazy {
    MediaStorageRepositoryFirebase(
        Firebase.storage.apply { maxDownloadRetryTimeMillis = MAX_DOWNLOAD_RETRY_MS }
    )
  }

  @Volatile private var cache: MediaFileCache? = null

  /**
   * The cache of downloaded media, in a `media` folder of the app's cache directory. Created on the
   * first call; later calls return the same instance, whatever [context] they pass.
   */
  fun mediaFileCache(context: Context): MediaFileCache =
      // checked again inside synchronized: two first calls at once must not both create one
      cache
          ?: synchronized(this) {
            cache
                ?: MediaFileCache(repository, File(context.applicationContext.cacheDir, CACHE_DIR))
                    .also { cache = it }
          }

  private const val CACHE_DIR = "media"

  /**
   * How long Firebase keeps retrying a download after errors (default: 10 minutes). Offline, a file
   * that isn't cached then fails after about this long instead of leaving the UI waiting, at the
   * cost of failing on short network drops. A download that is making progress isn't cut off.
   */
  private const val MAX_DOWNLOAD_RETRY_MS = 5_000L
}
