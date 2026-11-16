// Written by Alaabrine, with assistance from Cursor.

package com.github.se.amber.data.session

import coil3.SingletonImageLoader
import com.github.se.amber.data.account.DeviceLocalPhone
import com.github.se.amber.data.media.image.MediaImageMemory
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MediaRepositoryProvider
import com.google.firebase.Firebase
import com.google.firebase.app

/**
 * Holds a single [SessionRepository], built lazily by `factory` on first access.
 *
 * ViewModels hold this provider and read [repository] when the user signs out, so opening a screen
 * doesn't build the media layer. With the default factory, the default Firebase app must be
 * initialized before [repository] is first accessed; if building fails, the next access tries
 * again.
 */
class SessionRepositoryProvider(
    factory: () -> SessionRepository = { deviceRepository() },
) {
  val repository: SessionRepository by lazy(factory)

  companion object {
    /** The app-wide instance. Don't use it in tests. */
    val default = SessionRepositoryProvider()

    /**
     * Builds the repository that clears this phone and then signs out. Its tests pass their own
     * [mediaRepository] and [authRepository], so they don't build the app-wide ones that other
     * tests check.
     */
    internal fun deviceRepository(
        mediaRepository: MediaRepository = MediaRepositoryProvider.repository,
        authRepository: AuthRepository = AuthRepositoryProvider.repository,
    ): SessionRepository {
      val context = Firebase.app.applicationContext
      return DeviceSessionRepository(
          phone =
              DeviceLocalPhone(
                  mediaRepository = mediaRepository,
                  imageMemory = MediaImageMemory(SingletonImageLoader.get(context)),
                  cacheDir = context.cacheDir,
              ),
          authRepository = authRepository,
      )
    }
  }
}
