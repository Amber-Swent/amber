// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.image

import coil3.key.Keyer
import coil3.request.Options
import com.github.se.amber.model.media.MediaPicture

/**
 * Keys a [MediaPicture] in Coil's memory cache by its media, "media/{circleId}/{mediaId}", not by
 * its file: a picture added on this phone is decoded once, from the outbox, and is still found
 * after it is sent and its file moves to the cache. A media's file never changes, so the circle and
 * media ids are enough.
 *
 * Each media has a single entry, whatever size it is shown at: a request bigger than the decoded
 * picture decodes it again and replaces it, and one smaller uses it as it is.
 */
class MediaPictureKeyer : Keyer<MediaPicture> {
  override fun key(data: MediaPicture, options: Options): String =
      mediaPictureKey(data.circle.id, data.picture.id)
}

/** The memory cache key of the picture [mediaId] of [circleId] (see [MediaPictureKeyer]). */
internal fun mediaPictureKey(circleId: String, mediaId: String) =
    mediaPictureKeyPrefix(circleId) + mediaId

/** The start of every memory cache key of [circleId]'s pictures (see [MediaPictureKeyer]). */
internal fun mediaPictureKeyPrefix(circleId: String) = "media/$circleId/"
