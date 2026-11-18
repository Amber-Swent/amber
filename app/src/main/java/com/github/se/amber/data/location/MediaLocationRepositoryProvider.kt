// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.location

import com.github.se.amber.model.media.MediaRepositoryProvider

/**
 * Holds a single [MediaLocationRepository], built lazily by `factory` on first access.
 *
 * ViewModels should receive the repository as a constructor parameter (their factory reads it from
 * [default]), so tests can pass a fake instead. The default factory builds on the app's
 * [MediaRepositoryProvider.repository], so the same conditions apply to its first access.
 */
class MediaLocationRepositoryProvider(
    factory: () -> MediaLocationRepository = {
      ApprovedMediaLocationRepository(MediaRepositoryProvider.repository)
    },
) {
  val repository: MediaLocationRepository by lazy(factory)

  companion object {
    /** The app-wide instance. Don't use it in tests. */
    val default = MediaLocationRepositoryProvider()
  }
}
