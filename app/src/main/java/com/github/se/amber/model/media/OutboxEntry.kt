// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.media

/** How far a media of the outbox has got on its way to Firebase. */
enum class SendState {
  /** Saved on the phone, pending its send (e.g. waiting for a connection). */
  WAITING,

  /** Being sent right now. */
  SENDING,

  /**
   * Can't succeed by retrying (e.g. permission denied, or its file is missing): it won't be retried
   * on its own. The user can retry it ([MediaRepository.retrySend]) or discard it
   * ([MediaRepository.discardUnsent]).
   */
  FAILED,
}

/**
 * A media added on this phone and not in Firebase yet, as [MediaRepository.observeOutbox] shows it.
 * Its file is available through [MediaRepository.getFile], like a sent media's.
 *
 * @property item the media's metadata, as it will be written to Firestore.
 * @property state how far its send has got.
 */
data class OutboxEntry(val item: MediaItem, val state: SendState)
