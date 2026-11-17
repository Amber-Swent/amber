// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment

/**
 * Holds a single [AppointmentRepository], built lazily by `factory` on first access.
 *
 * ViewModels should receive the repository as a constructor parameter (their factory reads it from
 * [default]), so tests can pass a fake instead. With the default factory, the default Firebase app
 * must be initialized before [repository] is first accessed; if building fails, the next access
 * tries again.
 *
 * There must be one repository for the app. Its [AppointmentRepository.writeFailures] only reports
 * the writes made through it.
 */
class AppointmentRepositoryProvider(
    factory: () -> AppointmentRepository = { FirebaseAppointmentRepository() },
) {
  val repository: AppointmentRepository by lazy(factory)

  companion object {
    /** The app-wide instance. Don't use it in tests. */
    val default = AppointmentRepositoryProvider()
  }
}
