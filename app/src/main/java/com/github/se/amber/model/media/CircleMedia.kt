// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.media

/**
 * A media of a care circle as a gallery shows it, whether it is in Firebase or still on its way
 * there, as [MediaRepository.observeCircleMedia] lists it.
 *
 * @property item the media's metadata.
 * @property sendState how far its send has got while it is in the outbox of this phone; null once
 *   it is in Firebase.
 */
data class CircleMedia(val item: MediaItem, val sendState: SendState?)
