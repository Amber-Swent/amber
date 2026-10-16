// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.media

import android.util.Log
import androidx.work.WorkerFactory
import com.github.se.amber.data.media.MediaFileCache
import com.github.se.amber.data.media.MediaFileStorageFirebase
import com.github.se.amber.data.media.MediaOutbox
import com.github.se.amber.data.media.MediaRepositoryFirebase
import com.github.se.amber.data.media.MediaSendWorkerFactory
import com.github.se.amber.data.media.MediaSendWorkerScheduler
import com.github.se.amber.data.media.MediaSender
import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.firestore.firestore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Holds the app's single [MediaRepository], the one place where the media layer is built. Its
 * building blocks (the file storage, the file cache, the outbox, Firestore and the send scheduler)
 * stay inside: only the repository and the [workerFactory] are exposed.
 *
 * Being an `object`, it exists once, so every caller shares the same repository; this matters for
 * its cache and its outbox, each of which must be the only one using its folder. ViewModels should
 * receive the repository as a constructor parameter (their factory reads it here), so tests can
 * pass a fake instead.
 */
object MediaRepositoryProvider {

  /** Runs the media layer's own background work, for as long as the process lives. */
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  /**
   * The Firebase-backed repository, caching files in a `media` folder of the app's cache directory
   * and keeping unsent media in a `media-outbox` folder of its files directory, which the system
   * never empties (the outbox may hold the only copy of a media). Its sends run as WorkManager
   * jobs. Also the [MediaSender], kept private so that only [workerFactory] hands it out.
   *
   * Built on first use: [lazy] makes concurrent first uses wait for a single instance, and if
   * building fails (Firebase not initialized yet), the next use tries again. The app's context
   * comes from Firebase, which the app initializes before any activity or background job starts.
   *
   * Once built, it schedules in the background the send of every unsent media left without a job
   * ([MediaRepositoryFirebase.scheduleWaitingSends]). The first use comes from a screen showing
   * media, from the picture loader, or from a send job, so it happens before the user can see such
   * a media waiting.
   */
  private val firebaseRepository: MediaRepositoryFirebase by lazy {
    val context = Firebase.app.applicationContext
    MediaRepositoryFirebase(
            fileStorage = MediaFileStorageFirebase(),
            cache = MediaFileCache(File(context.cacheDir, CACHE_DIR)),
            outbox = MediaOutbox(File(context.filesDir, OUTBOX_DIR)),
            firestore = Firebase.firestore,
            scheduler = MediaSendWorkerScheduler(context),
        )
        .also { repository ->
          scope.launch {
            try {
              repository.scheduleWaitingSends()
            } catch (e: Exception) {
              // those media wait for the next start; an error left uncaught here would crash the
              // app
              Log.w(TAG, "Couldn't schedule the sends of the unsent media", e)
            }
          }
        }
  }

  /** The app's repository, built on first use. */
  val repository: MediaRepository
    get() = firebaseRepository

  /**
   * Creates the media background jobs with this same repository as their [MediaSender]. Only for
   * the app's WorkManager setup: it is how the jobs get the sender without exposing it.
   */
  val workerFactory: WorkerFactory = MediaSendWorkerFactory { firebaseRepository }

  private const val TAG = "MediaRepositoryProvider"
  private const val CACHE_DIR = "media"
  private const val OUTBOX_DIR = "media-outbox"
}
