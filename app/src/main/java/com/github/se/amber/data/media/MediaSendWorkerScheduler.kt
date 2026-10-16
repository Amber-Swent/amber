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
 * a new job, with an attempt count of 0. [reschedule] does so even if the old job hasn't ended yet,
 * which is what `retrySend` relies on.
 *
 * A job runs only with a connection. After each [SendResult.RETRY], its next run is due 30 seconds
 * × 2^(runs so far − 1) after that RETRY, at most 5 hours; runs the system stopped count among the
 * runs (see [MediaSendWorker]). Each job is tagged with its circle, so [cancelCircle] finds them
 * all, and with a tag common to every media send, for [cancelAll].
 *
 * @param context any context of the app; WorkManager keeps only the application context.
 */
class MediaSendWorkerScheduler(private val context: Context) : MediaSendScheduler {

  // Asked on every call rather than in the constructor, so building the repository (and this
  // scheduler with it) doesn't set up WorkManager; once set up, getInstance returns it at once.
  private val workManager: WorkManager
    get() = WorkManager.getInstance(context)

  override fun schedule(circleId: String, mediaId: String) {
    // KEEP leaves a job that is waiting or running alone, and replaces one that has ended
    enqueue(circleId, mediaId, ExistingWorkPolicy.KEEP)
  }

  override fun reschedule(circleId: String, mediaId: String) {
    // REPLACE also cancels a job still finishing, which KEEP would keep
    enqueue(circleId, mediaId, ExistingWorkPolicy.REPLACE)
  }

  private fun enqueue(circleId: String, mediaId: String, policy: ExistingWorkPolicy) {
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
    workManager.enqueueUniqueWork(workName(circleId, mediaId), policy, request)
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

  internal companion object {
    /** The wait before the second attempt; each later wait is twice the one before. */
    private const val FIRST_RETRY_DELAY_SECONDS = 30L

    const val ALL_SENDS_TAG = "media-send"

    // A Firestore id can't contain "/", so these names can't mix up two circles or two media
    fun circleTag(circleId: String) = "$ALL_SENDS_TAG/$circleId"

    fun workName(circleId: String, mediaId: String) = "$ALL_SENDS_TAG/$circleId/$mediaId"
  }
}
