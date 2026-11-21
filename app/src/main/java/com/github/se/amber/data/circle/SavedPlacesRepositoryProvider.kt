// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.circle

/**
 * Holds a single [SavedPlacesRepository], built lazily by `factory` on first access, like
 * [CareCircleRepositoryProvider].
 */
class SavedPlacesRepositoryProvider(
    factory: () -> SavedPlacesRepository = { FirebaseCareCircleRepository() },
) {
  val repository: SavedPlacesRepository by lazy(factory)

  companion object {
    /** The app-wide instance. Don't use it in tests. */
    val default = SavedPlacesRepositoryProvider()
  }
}
