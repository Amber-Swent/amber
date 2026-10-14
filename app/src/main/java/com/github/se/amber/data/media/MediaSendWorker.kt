// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.workDataOf

/**
 * The background job sending one media of the outbox: WorkManager runs it, even after the app was
 * closed, and it calls [sender] once per run.
 *
 * WorkManager creates it itself, possibly in a new app process, so its [sender] can't come from the
 * code that scheduled it: [MediaSendWorkerFactory] passes it in. Its input is [inputData].
 *
 * WorkManager stops a run after 10 minutes, or when the connection is lost. The send is then
 * cancelled (its entry goes back to WAITING), and WorkManager runs it again; that run counts as an
 * attempt. The upload's retry limit (`MediaFileStorageFirebase.MAX_UPLOAD_RETRY_MS`) is kept well
 * under 10 minutes for this reason: offline, the upload gives up and the send reports a retry
 * before WorkManager stops it. A slow upload that keeps progressing is still stopped at 10 minutes.
 */
class MediaSendWorker(
    context: Context,
    params: WorkerParameters,
    private val sender: MediaSender,
) : CoroutineWorker(context, params) {

  /**
   * Makes one send attempt and tells WorkManager what to do next: stop on [SendResult.DONE] or
   * [SendResult.FAILED], run again later on [SendResult.RETRY]. Attempt number [MAX_ATTEMPTS] is
   * made with `lastAttempt = true`, so it never asks to run again.
   *
   * @return [Result.failure] without sending if its input lacks an id.
   */
  override suspend fun doWork(): Result {
    val circleId = inputData.getString(KEY_CIRCLE_ID)
    val mediaId = inputData.getString(KEY_MEDIA_ID)
    if (circleId == null || mediaId == null) return Result.failure()
    // runAttemptCount counts the earlier attempts of this job: 0 on the first one
    val lastAttempt = runAttemptCount + 1 >= MAX_ATTEMPTS
    return when (sender.send(circleId, mediaId, lastAttempt)) {
      SendResult.DONE -> Result.success()
      SendResult.FAILED -> Result.failure()
      // a sender breaking its contract on the last attempt must not loop forever
      SendResult.RETRY -> if (lastAttempt) Result.failure() else Result.retry()
    }
  }

  companion object {
    /**
     * How many times a send is attempted before its media is marked FAILED. The waits set by
     * [MediaSendWorkerScheduler] (30 seconds, doubling each time) add up to about 4 hours 15
     * minutes, so the last attempt comes at least that long after the first. They pass even
     * offline; an attempt also waits for a connection, so being offline delays it only when it
     * lasts past the end of a wait.
     */
    const val MAX_ATTEMPTS = 10

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
 * @param sender gives the [MediaSender], only when a worker is created, so building this factory
 *   doesn't build the repository.
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
        MediaSendWorker(appContext, workerParameters, sender())
      } else {
        null
      }
}
