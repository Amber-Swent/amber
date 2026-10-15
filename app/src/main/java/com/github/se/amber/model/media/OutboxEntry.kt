// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.media

/** How far a media of the outbox has got on its way to Firebase. */
enum class SendState {
  /**
   * Saved on the phone, pending its send: waiting for a connection, or for its next attempt after
   * one that failed for a reason that may go away.
   */
  WAITING,

  /**
   * Being sent right now. If the app is killed meanwhile, the media is back to [WAITING] the next
   * time the app starts, and its send is attempted again.
   */
  SENDING,

  /**
   * Won't be sent again on its own: retrying can't help (e.g. permission denied, or its file is
   * missing), or its send failed too many times in a row (10; a run the system stops doesn't
   * count). The user can retry it ([MediaRepository.retrySend]) or discard it
   * ([MediaRepository.discardUnsent]).
   */
  FAILED,
}

/**
 * A media added on this phone and not in Firebase yet, as [MediaRepository.observeOutbox] shows it.
 * Its file, if it has one, is available through [MediaRepository.getFile], like a sent media's.
 *
 * @property item the media's metadata, as it will be written to Firestore.
 * @property state how far its send has got.
 */
data class OutboxEntry(val item: MediaItem, val state: SendState)
