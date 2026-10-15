// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException

/**
 * The background job sending one media of the outbox: WorkManager runs it, even after the app was
 * closed, and it makes one send attempt per run.
 *
 * WorkManager creates it itself, possibly in a new app process, so its sender can't come from the
 * code that scheduled it: [MediaSendWorkerFactory] passes it in. Its input is [inputData].
 *
 * WorkManager may stop a run: as soon as the connection is lost, or after about 10 minutes. The
 * send is then cancelled (its entry goes back to WAITING) and WorkManager runs it again once
 * online, without waiting first. The stopped run still counts as an attempt, so on a connection
 * that keeps dropping, or for an upload too slow to finish in 10 minutes, a media can end FAILED
 * well before the waits of [MAX_ATTEMPTS] have passed.
 *
 * When the phone is connected but Firebase can't be reached, WorkManager doesn't stop the run: the
 * upload's own retry limit (`MediaFileStorageFirebase.MAX_UPLOAD_RETRY_MS`, well under 10 minutes)
 * makes it give up, and the send reports a retry.
 */
class MediaSendWorker(
    context: Context,
    params: WorkerParameters,
    private val sender: () -> MediaSender,
) : CoroutineWorker(context, params) {

  /**
   * Makes one send attempt and tells WorkManager what to do next: stop on [SendResult.DONE] or
   * [SendResult.FAILED], run again later on [SendResult.RETRY]. Attempt number [MAX_ATTEMPTS] (or
   * any later one) is made with `lastAttempt = true`, so it never asks to run again.
   *
   * Getting the sender or sending shouldn't throw, but if it does (e.g. the repository can't be
   * built in this process), the attempt counts as a [SendResult.RETRY] rather than ending the job,
   * which would leave the media's outbox entry without a job.
   *
   * @return [Result.failure] without sending if its input lacks an id.
   */
  override suspend fun doWork(): Result {
    val circleId = inputData.getString(KEY_CIRCLE_ID)
    val mediaId = inputData.getString(KEY_MEDIA_ID)
    if (circleId == null || mediaId == null) return Result.failure()
    // runAttemptCount counts the earlier attempts of this job: 0 on the first one
    val lastAttempt = runAttemptCount + 1 >= MAX_ATTEMPTS
    val result =
        try {
          sender().send(circleId, mediaId, lastAttempt)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.w(TAG, "Send of media $mediaId failed unexpectedly", e)
          SendResult.RETRY
        }
    return when (result) {
      SendResult.DONE -> Result.success()
      SendResult.FAILED -> Result.failure()
      // a RETRY on the last attempt (a sender breaking its contract, or the error above) must not
      // loop forever
      SendResult.RETRY -> if (lastAttempt) Result.failure() else Result.retry()
    }
  }

  companion object {
    /**
     * How many times a send is attempted before its media is marked FAILED. When every attempt runs
     * to its end, the waits set by [MediaSendWorkerScheduler] (30 seconds, doubling each time) add
     * up to about 4 hours 15 minutes between the first and the last attempt. They pass even
     * offline; an attempt also waits for a connection, so being offline delays it only when it
     * lasts past the end of a wait. Runs stopped by WorkManager count too but don't wait (see the
     * class KDoc), so the last attempt can come much sooner.
     */
    const val MAX_ATTEMPTS = 10

    private const val TAG = "MediaSendWorker"
    private const val KEY_CIRCLE_ID = "circleId"
    private const val KEY_MEDIA_ID = "mediaId"

    /** The input of the job sending the media [mediaId] of [circleId]. */
    fun inputData(circleId: String, mediaId: String): Data =
        workDataOf(KEY_CIRCLE_ID to circleId, KEY_MEDIA_ID to mediaId)
  }
}

/**
 * Creates the media workers with what they need, since WorkManager can only pass them a context and
 * their parameters. The app gives it to WorkManager at startup; ViewModels never use it.
 *
 * @param sender gives the [MediaSender]; called only when a worker runs, so building this factory
 *   or a worker doesn't build the repository.
 */
class MediaSendWorkerFactory(private val sender: () -> MediaSender) : WorkerFactory() {

  /**
   * A [MediaSendWorker] if [workerClassName] names it, else null, which lets WorkManager create the
   * worker itself.
   */
  override fun createWorker(
      appContext: Context,
      workerClassName: String,
      workerParameters: WorkerParameters,
  ): ListenableWorker? =
      if (workerClassName == MediaSendWorker::class.java.name) {
        MediaSendWorker(appContext, workerParameters, sender)
      } else {
        null
      }
}
