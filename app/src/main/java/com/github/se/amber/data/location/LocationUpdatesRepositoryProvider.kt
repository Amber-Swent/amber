// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.location

import com.google.firebase.Firebase
import com.google.firebase.app

/**
 * Holds a single [LocationUpdatesRepository], built lazily by `factory` on first access.
 *
 * ViewModels should receive the repository as a constructor parameter (their factory reads it from
 * [default]), so tests can pass a fake instead. The default factory takes the app's context from
 * Firebase, as PlaceSearchRepositoryProvider does, so the default Firebase app must be initialized
 * before [repository] is first accessed.
 */
class LocationUpdatesRepositoryProvider(
    factory: () -> LocationUpdatesRepository = {
      FusedLocationUpdatesRepository(Firebase.app.applicationContext)
    },
) {
  val repository: LocationUpdatesRepository by lazy(factory)

  companion object {
    /** The app-wide instance. Don't use it in tests. */
    val default = LocationUpdatesRepositoryProvider()
  }
}
