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
   * attempt): nothing left to do.
   */
  DONE,

  /**
   * It failed for a reason that may go away (e.g. offline): try again later. Its outbox entry is
   * back to [SendState.WAITING] until then.
   */
  RETRY,

  /**
   * Retrying won't help (e.g. permission denied, or its file is missing), or this was the last
   * attempt. Its outbox entry is now [SendState.FAILED]: it is sent again only through
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
   * Never throws, except [kotlinx.coroutines.CancellationException] when its job is cancelled; its
   * entry, if still in the outbox, is then back to [SendState.WAITING]. Unexpected errors count as
   * failures that may go away.
   *
   * @param lastAttempt true if no other attempt will follow on its own: a failure that would give
   *   [SendResult.RETRY] then gives [SendResult.FAILED], so the media doesn't stay
   *   [SendState.WAITING] forever.
   */
  suspend fun send(circleId: String, mediaId: String, lastAttempt: Boolean): SendResult
}

/**
 * Runs [MediaSender.send] in a background job, even if the app was closed meanwhile. Being an
 * interface, it can be replaced by a fake in tests.
 *
 * The job runs only while the phone has a connection: offline, it waits without making attempts.
 * While it gives [SendResult.RETRY], it is run again later, waiting longer each time, up to a fixed
 * number of attempts; the last one is made with `lastAttempt = true`, so a media that keeps failing
 * ends [SendState.FAILED] instead of waiting forever. A connection lost during an attempt makes
 * that attempt fail, and it counts.
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
   * For [MediaRepository.retrySend] only, once the entry is [SendState.FAILED]: the job that failed
   * may still be finishing when the user asks to retry, and [schedule] would then keep that job and
   * schedule nothing, leaving the entry [SendState.WAITING] without a job.
   *
   * Never call it while the entry may be [SendState.WAITING] or [SendState.SENDING]: it would
   * cancel a send under way and restart its attempt count.
   */
  fun reschedule(circleId: String, mediaId: String)

  /** Cancels the scheduled send of the media [mediaId] of [circleId], if any. */
  fun cancel(circleId: String, mediaId: String)

  /** Cancels the scheduled sends of every media of [circleId]. */
  fun cancelCircle(circleId: String)

  /** Cancels every scheduled send, of every circle. */
  fun cancelAll()
}
