// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.SendState

/** How a [MediaSender.send] attempt ended. */
enum class SendResult {
  /** The media is in Firebase (or no longer in the outbox): nothing left to do. */
  DONE,

  /** It failed for a reason that may go away (e.g. offline): try again later. */
  RETRY,

  /** Firebase refused it: trying again won't help. Its outbox entry is now [SendState.FAILED]. */
  FAILED,
}

/**
 * Sends one media of the outbox to Firebase: its file to Storage first, then its metadata to
 * Firestore. Implemented by [MediaRepositoryFirebase] and called only by the background send job,
 * never by ViewModels, which is why it isn't part of [MediaRepository].
 */
fun interface MediaSender {
  /**
   * Sends the media [mediaId] of [circleId] from the outbox. Safe to call again after any failure
   * or crash: every step writes to the same Storage path and the same document.
   */
  suspend fun send(circleId: String, mediaId: String): SendResult
}

/**
 * Runs [MediaSender.send] in the background, once a connection is available, even if the app was
 * closed meanwhile. Being an interface, it can be replaced by a fake in tests.
 */
interface MediaSendScheduler {
  /** Plans the send of the media [mediaId] of [circleId]; no effect if it is already planned. */
  fun schedule(circleId: String, mediaId: String)

  /** Cancels the planned send of the media [mediaId] of [circleId], if any. */
  fun cancel(circleId: String, mediaId: String)

  /** Cancels the planned sends of every media of [circleId]. */
  fun cancelCircle(circleId: String)

  /** Cancels every planned send, of every circle. */
  fun cancelAll()
}
