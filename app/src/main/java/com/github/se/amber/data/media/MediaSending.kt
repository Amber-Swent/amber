// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.SendState

/** How a [MediaSender.send] attempt ended. */
enum class SendResult {
  /**
   * The media is in Firebase, or no longer in the outbox (e.g. discarded, or sent by an earlier
   * attempt), or [SendState.FAILED] until the user retries it: nothing left for this job to do.
   */
  DONE,

  /**
   * Not sent yet, for a reason that may go away: try again later. Usually a failed attempt (e.g.
   * Firestore unavailable), recorded in its outbox entry, which is back to [SendState.WAITING]
   * until then. Nothing is recorded when the outbox itself couldn't be updated, or when the attempt
   * didn't own its entry (see [MediaSender.send]).
   */
  RETRY,

  /**
   * Retrying won't help (e.g. permission denied, the file refused before anything is sent, or its
   * file is missing), or its send has now failed [MediaSender.MAX_FAILED_ATTEMPTS] times in a row.
   * Its outbox entry is now [SendState.FAILED]: it is sent again only through
   * [MediaRepository.retrySend].
   */
  FAILED,
}

/**
 * Sends one media of the outbox to Firebase. Implemented by [MediaRepositoryFirebase] and called
 * only by the background send job, never by ViewModels, which is why it isn't part of
 * [MediaRepository].
 */
fun interface MediaSender {
  /**
   * Makes one attempt at sending the media [mediaId] of [circleId] from the outbox:
   * 1. marks its entry [SendState.SENDING];
   * 2. uploads its file to Storage (a [MediaItem.Text] has none);
   * 3. creates its Firestore document, unless it already exists;
   * 4. moves its file into the cache and removes its entry from the outbox.
   *
   * The file goes first, so a Firestore document never points at a missing Storage file.
   *
   * Safe to call again after any failure or crash, because each step can be repeated: the upload
   * rewrites the same Storage path with the same bytes, and step 3 never overwrites a document an
   * earlier attempt created, so changes made to it since (e.g. its moderation status) are kept.
   *
   * A failure that may go away (an error Firebase reports, network ones included, or an attempt
   * still running after [ATTEMPT_TIMEOUT_MS]) records a failed attempt in the media's outbox entry
   * ([MediaOutbox.recordFailedAttempt]) and gives [SendResult.RETRY], or [SendResult.FAILED] at the
   * [MAX_FAILED_ATTEMPTS]th; [MediaRepository.retrySend] starts the count again. A cancellation (a
   * run the system stopped) records nothing and is rethrown: it only sets the entry's state back to
   * [SendState.WAITING] ([MediaOutbox.resetSending]), leaving its other fields as they are, its
   * failed-attempt count included. [MediaSendScheduler.reschedule] cancels the old job right after
   * [MediaRepository.retrySend] reset the entry, so writing more could undo that reset.
   *
   * Never throws otherwise, except an [Error] (a bug, or out of memory), rethrown after the same
   * reset: unexpected exceptions count as failures that may go away. Failures retrying can't fix
   * give [SendResult.FAILED] at once, nothing counted: Firestore refusing the user, the file
   * refused before anything is sent (an [IllegalArgumentException]), or the file of a picture or an
   * audio gone while its document doesn't exist. If its file is gone but its document exists, an
   * earlier attempt sent it: the entry is removed and the result is [SendResult.DONE]. If the
   * outbox can't be updated at the start or the end of the attempt, the result is
   * [SendResult.RETRY], nothing counted.
   *
   * An attempt may find that it no longer owns its entry when it ends: the outbox records nothing
   * because the entry is gone or no longer [SendState.SENDING] (e.g. discarded, or put back by a
   * late cancellation of an older job). It then gives [SendResult.DONE] if the entry is gone, and
   * otherwise [SendResult.RETRY] without counting: [SendResult.DONE] would end the job and leave
   * the entry without one. For the same reason, an entry already [SendState.SENDING] when the
   * attempt starts gives [SendResult.RETRY].
   */
  suspend fun send(circleId: String, mediaId: String): SendResult

  companion object {
    /**
     * How many failed attempts in a row end a send [SendResult.FAILED]. With the waits of
     * [MediaSendWorkerScheduler], 10 failed attempts take about 4 hours 15 minutes, counting from
     * the first.
     */
    const val MAX_FAILED_ATTEMPTS = 10

    /**
     * The longest one attempt may take: 9 minutes, under the system's 10-minute limit per run, so
     * an attempt too slow (a big upload on a slow link, or a step that hangs) fails and counts,
     * instead of being stopped, uncounted, run after run. The minute left covers starting the run
     * and recording the failure.
     */
    const val ATTEMPT_TIMEOUT_MS = 9 * 60 * 1000L
  }
}

/**
 * Runs [MediaSender.send] in a background job, even if the app was closed meanwhile. Being an
 * interface, it can be replaced by a fake in tests.
 *
 * The job runs only while the phone has a connection: offline, it doesn't run at all, so being
 * offline costs nothing and counts nothing. While [MediaSender.send] gives [SendResult.RETRY], the
 * job runs again later, waiting longer each time.
 *
 * The job has no limit of its own: it ends when the send gives [SendResult.DONE] or
 * [SendResult.FAILED]. Failures, timeouts included, count toward [MediaSender.MAX_FAILED_ATTEMPTS];
 * runs the system stops (connection lost, quota, battery saver) count nothing, so a phone that
 * stops every run keeps trying, spaced out by the waits.
 *
 * It passes only the circle and media ids to the job, which gets its [MediaSender] when it runs:
 * the scheduler never needs the repository, so the repository can be built with the scheduler.
 *
 * Cancelling never touches the outbox: the entry stays, only its send stops. A send already under
 * way may still complete.
 */
interface MediaSendScheduler {
  /**
   * Schedules the send of the media [mediaId] of [circleId]; no effect if it is already scheduled
   * or running.
   */
  fun schedule(circleId: String, mediaId: String)

  /**
   * Schedules a new send of the media [mediaId] of [circleId], replacing its current job if any.
   * Called by [MediaRepository.retrySend] right after it moved a [SendState.FAILED] entry back to
   * [SendState.WAITING]; nothing else may call it. The job that failed may still be finishing then,
   * and [schedule] would keep that job and schedule nothing, leaving the entry without a job.
   * Called at any other time, it could cancel a send under way.
   */
  fun reschedule(circleId: String, mediaId: String)

  /** Cancels the scheduled send of the media [mediaId] of [circleId], if any. */
  fun cancel(circleId: String, mediaId: String)

  /** Cancels the scheduled sends of every media of [circleId]. */
  fun cancelCircle(circleId: String)

  /** Cancels every scheduled send, of every circle. */
  fun cancelAll()
}
