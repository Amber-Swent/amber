// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.directions

import com.google.firebase.Firebase
import com.google.firebase.app
import java.io.File

/**
 * Holds a single [RouteRepository], built lazily by `factory` on first access.
 *
 * ViewModels should receive the repository as a constructor parameter (their factory reads it from
 * [default]), so tests can pass a fake instead. The default factory keeps the last route in the
 * app's cache, taking the context from Firebase, so the default Firebase app must be initialized
 * before [repository] is first accessed.
 */
class RouteRepositoryProvider(
    factory: () -> RouteRepository = {
      val cacheDir = Firebase.app.applicationContext.cacheDir
      FirebaseRouteRepository(RouteFile(File(cacheDir, LAST_ROUTE_FILE_NAME)))
    },
) {
  val repository: RouteRepository by lazy(factory)

  companion object {
    /** The app-wide instance. Don't use it in tests. */
    val default = RouteRepositoryProvider()
  }
}
