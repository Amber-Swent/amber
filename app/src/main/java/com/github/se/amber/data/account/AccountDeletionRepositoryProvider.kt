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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

  /**
   * Finishes a deletion started by an earlier launch, if [marker] says there was one. Otherwise
   * nothing is built, so a normal launch doesn't touch Firebase.
   *
   * It doesn't wait for the UI: clearing stops Firestore and restarts the app, wherever it is.
   *
   * @param marker Tells whether a deletion was started.
   * @param scope Where it runs.
   */
  fun resumeInterruptedDeletion(
      marker: DeletionMarker,
      scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
  ) {
    if (!marker.isSet) return
    scope.launch {
      // A failure here must not stop the app from opening; the next launch tries again
      runCatching { repository.finishInterruptedDeletion() }
    }
  }

  companion object {
    /** The app-wide instance. Don't use it in tests. */
    val default = AccountDeletionRepositoryProvider()

    private fun firebaseRepository(): AccountDeletionRepository {
      val context = Firebase.app.applicationContext
      val marker = SharedPreferencesDeletionMarker(context)
      return FirebaseAccountDeletionRepository(
          DeviceLocalAccountData(
              mediaRepository = MediaRepositoryProvider.repository,
              authRepository = AuthRepositoryProvider.repository,
              imageMemory = MediaImageMemory(SingletonImageLoader.get(context)),
              cacheDir = context.cacheDir,
              firestore = Firebase.firestore,
              marker = marker,
              restartApp = { restartApp(context, ::stopApp) },
              stopApp = ::stopApp,
          ),
          marker,
      )
    }

    /** Opens the app's first screen in a new process, so nothing still uses the old Firestore. */
    internal fun restartApp(context: Context, stopApp: () -> Unit) {
      // The process must end whatever happens here: Firestore can't be used any more
      runCatching {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        context.startActivity(Intent.makeRestartActivityTask(launchIntent?.component))
      }
      stopApp()
    }

    private fun stopApp() {
      Runtime.getRuntime().exit(0)
    }
  }
}
