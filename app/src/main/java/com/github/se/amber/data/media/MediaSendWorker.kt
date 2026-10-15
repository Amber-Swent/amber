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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * The background job sending one media of the outbox: WorkManager runs it, even after the app was
 * closed, and it makes one send attempt per run.
 *
 * WorkManager creates it itself, possibly in a new app process, so its sender can't come from the
 * code that scheduled it: [MediaSendWorkerFactory] passes it in. Its input is [inputData].
 *
 * It has no limit of its own on runs: the sender decides when to give up, after
 * [MediaSender.MAX_FAILED_ATTEMPTS] failed attempts. A run the system stops (connection lost,
 * quota, battery saver) cancels the send, which records no failed attempt; WorkManager still counts
 * it in `runAttemptCount`, so later waits grow (see [MediaSendWorkerScheduler]).
 */
class MediaSendWorker(
    context: Context,
    params: WorkerParameters,
    private val sender: () -> MediaSender,
) : CoroutineWorker(context, params) {

  /**
   * Makes one send attempt and tells WorkManager what to do next: stop on [SendResult.DONE] or
   * [SendResult.FAILED], run again later on [SendResult.RETRY].
   *
   * Getting the sender or sending shouldn't throw, but if it does (e.g. the repository can't be
   * built in this process), the run counts as a [SendResult.RETRY] rather than ending the job,
   * which would leave the media's outbox entry without a job. A cancellation is passed on only if
   * this run was really stopped; a stray one is a retry too, since WorkManager would otherwise
   * count it as a failure of the job. These errors record no failed attempt, so if one happens on
   * every run, the job keeps retrying, with waits growing up to 5 hours. They are logged as
   * warnings, then as errors once the job has run [MediaSender.MAX_FAILED_ATTEMPTS] times, when a
   * send keeping its contract would usually have ended: a stuck job then stands out in the logs.
   *
   * @return [Result.failure] without sending if its input lacks an id.
   */
  override suspend fun doWork(): Result {
    val circleId = inputData.getString(KEY_CIRCLE_ID)
    val mediaId = inputData.getString(KEY_MEDIA_ID)
    if (circleId == null || mediaId == null) return Result.failure()
    val result =
        try {
          sender().send(circleId, mediaId)
        } catch (e: CancellationException) {
          // rethrows if WorkManager stopped this run; otherwise the cancellation came from inside
          currentCoroutineContext().ensureActive()
          logUnexpected("Send of media $mediaId was cancelled although its run wasn't", e)
          SendResult.RETRY
        } catch (e: Exception) {
          logUnexpected("Send of media $mediaId failed unexpectedly", e)
          SendResult.RETRY
        }
    return when (result) {
      SendResult.DONE -> Result.success()
      SendResult.FAILED -> Result.failure()
      SendResult.RETRY -> Result.retry()
    }
  }

  /** Logs an unexpected error of this run, as an error once the job looks stuck (see [doWork]). */
  private fun logUnexpected(message: String, e: Exception) {
    if (runAttemptCount >= MediaSender.MAX_FAILED_ATTEMPTS) {
      Log.e(TAG, "$message, after $runAttemptCount earlier runs", e)
    } else {
      Log.w(TAG, message, e)
    }
  }

  companion object {
    internal const val TAG = "MediaSendWorker"
    internal const val KEY_CIRCLE_ID = "circleId"
    internal const val KEY_MEDIA_ID = "mediaId"

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
