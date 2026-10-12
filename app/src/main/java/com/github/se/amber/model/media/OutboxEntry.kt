// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.media

/** Where a media of the outbox is on its way to Firebase. */
enum class SendState {
  /** Saved on the phone, waiting to be sent (e.g. for a connection). */
  WAITING,

  /** Being sent right now. */
  SENDING,

  /**
   * Refused by Firebase (e.g. permission denied): it won't be retried on its own. The user can
   * retry it ([MediaRepository.retrySend]) or discard it ([MediaRepository.discardUnsent]).
   */
  FAILED,
}

/**
 * A media added on this phone and not in Firebase yet, as [MediaRepository.observeOutbox] shows it.
 * Its file is available through [MediaRepository.getFile], like a sent media's.
 *
 * @property item the media's metadata, as it will be written to Firestore.
 * @property state where its sending is.
 */
data class OutboxEntry(val item: MediaItem, val state: SendState)
