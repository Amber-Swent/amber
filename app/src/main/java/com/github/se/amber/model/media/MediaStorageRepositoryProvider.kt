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
 * Both are created on first use. Code that needs them should receive them as constructor parameters
 * rather than read this object itself, so tests can pass fakes instead.
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
   * Maximum time Firebase retries a download after a failure (Firebase's default: 10 minutes).
   * Offline, a file that isn't cached then fails after roughly this long instead of leaving the UI
   * waiting, at the cost of failing on short network drops. It limits retries, not the length of a
   * download.
   *
   * It is set on the app-wide `Firebase.storage` instance when [repository] is first created, so it
   * also applies to any other code using that instance from then on.
   */
  private const val MAX_DOWNLOAD_RETRY_MS = 5_000L
}
