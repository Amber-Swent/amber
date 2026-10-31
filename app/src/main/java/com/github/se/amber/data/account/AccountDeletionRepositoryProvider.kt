// Written by Neha Chakraborty, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.account

import android.content.Context
import android.content.Intent
import coil3.SingletonImageLoader
import com.github.se.amber.data.media.image.MediaImageMemory
import com.github.se.amber.model.authentication.AuthRepositoryProvider
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

    private fun firebaseRepository(): AccountDeletionRepository {
      val context = Firebase.app.applicationContext
      return FirebaseAccountDeletionRepository(
          DeviceLocalAccountData(
              mediaRepository = MediaRepositoryProvider.repository,
              authRepository = AuthRepositoryProvider.repository,
              imageMemory = MediaImageMemory(SingletonImageLoader.get(context)),
              cacheDir = context.cacheDir,
              firestore = Firebase.firestore,
              marker = SharedPreferencesDeletionMarker(context),
              restartApp = { restartApp(context) },
              stopApp = ::stopApp,
          ),
      )
    }

    /** Opens the app's first screen in a new process, so nothing still uses the old Firestore. */
    private fun restartApp(context: Context) {
      val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
      // The process must end whatever happens here: Firestore can't be used any more
      runCatching { context.startActivity(Intent.makeRestartActivityTask(launchIntent?.component)) }
      stopApp()
    }

    private fun stopApp() {
      Runtime.getRuntime().exit(0)
    }
  }
}
