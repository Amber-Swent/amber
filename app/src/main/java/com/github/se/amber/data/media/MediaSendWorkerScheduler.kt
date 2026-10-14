// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * The [MediaSendScheduler] of the app: each send is a WorkManager job running [MediaSendWorker].
 *
 * Each media has at most one job, a unique work named after its ids, so scheduling it twice keeps
 * the first job. Once that job has ended (sent, or [SendResult.FAILED]), scheduling it again starts
 * a new job, with an attempt count of 0: this is what `retrySend` relies on.
 *
 * A job runs only with a connection and, after each [SendResult.RETRY], waits longer before the
 * next attempt: 30 seconds, then twice as long each time. Each job is tagged with its circle, so
 * [cancelCircle] finds them all, and with a tag common to every media send, for [cancelAll].
 *
 * @param context any context of the app; WorkManager keeps only the application context.
 */
class MediaSendWorkerScheduler(private val context: Context) : MediaSendScheduler {

  // Asked on every call rather than kept: WorkManager is built on first use, and building it reads
  // the app's configuration, which reads the provider that builds this scheduler.
  private val workManager: WorkManager
    get() = WorkManager.getInstance(context)

  override fun schedule(circleId: String, mediaId: String) {
    val request =
        OneTimeWorkRequestBuilder<MediaSendWorker>()
            .setInputData(MediaSendWorker.inputData(circleId, mediaId))
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                FIRST_RETRY_DELAY_SECONDS,
                TimeUnit.SECONDS,
            )
            .addTag(ALL_SENDS_TAG)
            .addTag(circleTag(circleId))
            .build()
    // KEEP leaves a job that is waiting or running alone, and replaces one that has ended
    workManager.enqueueUniqueWork(workName(circleId, mediaId), ExistingWorkPolicy.KEEP, request)
  }

  override fun cancel(circleId: String, mediaId: String) {
    workManager.cancelUniqueWork(workName(circleId, mediaId))
  }

  override fun cancelCircle(circleId: String) {
    workManager.cancelAllWorkByTag(circleTag(circleId))
  }

  override fun cancelAll() {
    workManager.cancelAllWorkByTag(ALL_SENDS_TAG)
  }

  private companion object {
    /** The wait before the second attempt; each later wait is twice the one before. */
    const val FIRST_RETRY_DELAY_SECONDS = 30L

    const val ALL_SENDS_TAG = "media-send"

    // A Firestore id can't contain "/", so these names can't mix up two circles or two media
    fun circleTag(circleId: String) = "$ALL_SENDS_TAG/$circleId"

    fun workName(circleId: String, mediaId: String) = "$ALL_SENDS_TAG/$circleId/$mediaId"
  }
}
