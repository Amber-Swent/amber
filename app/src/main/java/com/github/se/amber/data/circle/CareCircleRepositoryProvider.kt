// This file was written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude
// Code.
package com.github.se.amber.data.circle

/**
 * Holds a single [CareCircleRepository], built lazily by `factory` on first access.
 *
 * ViewModels should receive the repository as a constructor parameter (their factory reads it from
 * [default]), so tests can pass a fake instead. With the default factory, the default Firebase app
 * must be initialized before [repository] is first accessed; if building fails, the next access
 * tries again.
 */
class CareCircleRepositoryProvider(
    factory: () -> CareCircleRepository = { FirebaseCareCircleRepository() },
) {
  val repository: CareCircleRepository by lazy(factory)

  companion object {
    /** The app-wide instance. Don't use it in tests. */
    val default = CareCircleRepositoryProvider()
  }
}
