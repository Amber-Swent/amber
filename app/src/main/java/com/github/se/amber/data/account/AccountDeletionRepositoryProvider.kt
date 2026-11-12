// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Alaabrine, with assistance from Cursor.

package com.github.se.amber.data.account

import coil3.SingletonImageLoader
import com.github.se.amber.data.media.image.MediaImageMemory
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MediaRepositoryProvider
import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.firestore.firestore

/**
 * Holds a single [AccountDeletionRepository], built lazily by `factory` on first access.
 *
 * ViewModels should receive the repository as a constructor parameter (their factory reads it from
 * [default]), so tests can pass a fake instead. With the default factory, the default Firebase app
 * must be initialized before [repository] is first accessed; if building fails, the next access
 * tries again.
 */
class AccountDeletionRepositoryProvider(
    factory: () -> AccountDeletionRepository = { firebaseRepository() },
) {
  val repository: AccountDeletionRepository by lazy(factory)

  companion object {
    /** The app-wide instance. Don't use it in tests. */
    val default = AccountDeletionRepositoryProvider()

    /**
     * Builds the Firebase repository. Its tests pass their own [mediaRepository] and
     * [authRepository], so they don't build the app-wide ones that other tests check.
     */
    internal fun firebaseRepository(
        mediaRepository: MediaRepository = MediaRepositoryProvider.repository,
        authRepository: AuthRepository = AuthRepositoryProvider.repository,
    ): AccountDeletionRepository {
      val context = Firebase.app.applicationContext
      val marker = SharedPreferencesDeletionMarker(context)
      val appProcess = AndroidAppProcess(context)
      val phone =
          DeviceLocalPhone(
              mediaRepository = mediaRepository,
              imageMemory = MediaImageMemory(SingletonImageLoader.get(context)),
              cacheDir = context.cacheDir,
          )
      return FirebaseAccountDeletionRepository(
          DeviceLocalAccountData(
              phone = phone,
              authRepository = authRepository,
              firestore = Firebase.firestore,
              marker = marker,
              restartApp = appProcess::restart,
              stopApp = appProcess::stop,
          ),
          marker,
          authRepository,
      )
    }
  }
}
