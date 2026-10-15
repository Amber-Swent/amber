// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.media

import androidx.work.WorkerFactory
import com.github.se.amber.data.media.MediaFileCache
import com.github.se.amber.data.media.MediaFileStorageFirebase
import com.github.se.amber.data.media.MediaRepositoryFirebase
import com.github.se.amber.data.media.MediaSendWorkerFactory
import com.github.se.amber.data.media.MediaSender
import com.google.firebase.Firebase
import com.google.firebase.app
import java.io.File

/**
 * Holds the app's single [MediaRepository], the one place where the media layer is built. Its
 * building blocks (the file storage and the file cache) stay inside: only the repository and the
 * [workerFactory] are exposed.
 *
 * Being an `object`, it exists once, so every caller shares the same repository; this matters for
 * its cache, which must be the only one using its folder. ViewModels should receive the repository
 * as a constructor parameter (their factory reads it here), so tests can pass a fake instead.
 */
object MediaRepositoryProvider {

  /**
   * The Firebase-backed repository, caching files in a `media` folder of the app's cache directory.
   * Also the [MediaSender], kept private so that only [workerFactory] hands it out.
   *
   * Built on first use: [lazy] makes concurrent first uses wait for a single instance, and if
   * building fails (Firebase not initialized yet), the next use tries again. The app's context
   * comes from Firebase, which the app initializes before any activity or background job starts.
   */
  private val firebaseRepository: MediaRepositoryFirebase by lazy {
    MediaRepositoryFirebase(
        fileStorage = MediaFileStorageFirebase(),
        cache = MediaFileCache(File(Firebase.app.applicationContext.cacheDir, CACHE_DIR)),
    )
  }

  /** The app's repository, built on first use. */
  val repository: MediaRepository
    get() = firebaseRepository

  /**
   * Creates the media background jobs with this same repository as their [MediaSender]. Only for
   * the app's WorkManager setup: it is how the jobs get the sender without exposing it.
   */
  val workerFactory: WorkerFactory = MediaSendWorkerFactory { firebaseRepository }

  private const val CACHE_DIR = "media"
}
