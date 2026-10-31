// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.image

import coil3.ImageLoader

/**
 * Clears the media pictures [imageLoader] keeps decoded in memory, so that private photos don't
 * stay there after sign-out or after leaving a circle. Build it with the app's image loader
 * (`SingletonImageLoader.get(context)`).
 *
 * Used only by the session cleanup, after it has cleared the local media through
 * `MediaRepository.clearLocalMedia`; not by screens or ViewModels. Run it once the circle's screens
 * are gone: a picture still loading may otherwise be put back in memory. Only account deletion
 * (`DeviceLocalAccountData`) uses it for now.
 */
class MediaImageMemory(private val imageLoader: ImageLoader) {

  /**
   * Removes the pictures of [circleId], and only those: call it when the user leaves that circle.
   *
   * @throws IllegalArgumentException if [circleId] is blank or contains '/': it could then match
   *   other circles' pictures.
   */
  fun clearCircle(circleId: String) {
    require(circleId.isNotBlank() && '/' !in circleId) { "Invalid circle id: '$circleId'" }
    val cache = imageLoader.memoryCache ?: return
    val prefix = mediaPictureKeyPrefix(circleId)
    cache.keys.filter { it.key.startsWith(prefix) }.forEach { cache.remove(it) }
  }

  /**
   * Removes every decoded picture, of every circle: call it on sign-out. It empties the whole
   * memory cache, so also any other image the app has shown.
   */
  fun clearAll() {
    imageLoader.memoryCache?.clear()
  }
}
