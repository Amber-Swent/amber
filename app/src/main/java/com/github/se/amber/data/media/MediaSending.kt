// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.SendState

/** How a [MediaSender.send] attempt ended. */
enum class SendResult {
  /**
   * The media is in Firebase, or no longer in the outbox (e.g. discarded, or sent by an earlier
   * attempt): nothing left to do.
   */
  DONE,

  /** It failed for a reason that may go away (e.g. offline): try again later. */
  RETRY,

  /**
   * Trying again won't help (e.g. permission denied). Its outbox entry is now [SendState.FAILED].
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
   * Sends the media [mediaId] of [circleId] from the outbox: marks it [SendState.SENDING], uploads
   * its file to Storage, writes its metadata to Firestore, then moves the file into the cache and
   * removes it from the outbox. The file goes first so a Firestore document never points at a
   * missing Storage file.
   *
   * Idempotent (doing it twice has the same effect as once): every attempt writes to the same
   * Storage path and the same document, so it can be called again after any failure or crash.
   *
   * Never throws, except [kotlinx.coroutines.CancellationException]: unexpected errors give
   * [SendResult.RETRY], so a bug never loses a media.
   */
  suspend fun send(circleId: String, mediaId: String): SendResult
}

/**
 * Runs [MediaSender.send] in the background once a connection is available, even if the app was
 * closed meanwhile, and runs it again later (waiting longer each time) while it gives
 * [SendResult.RETRY]. Being an interface, it can be replaced by a fake in tests.
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

  /** Cancels the scheduled send of the media [mediaId] of [circleId], if any. */
  fun cancel(circleId: String, mediaId: String)

  /** Cancels the scheduled sends of every media of [circleId]. */
  fun cancelCircle(circleId: String)

  /** Cancels every scheduled send, of every circle. */
  fun cancelAll()
}
