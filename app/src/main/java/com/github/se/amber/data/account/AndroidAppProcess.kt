// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.account

import android.content.Context
import android.content.Intent

/**
 * Ends the app's process, once the device is cleared after an account deletion: Firestore is then
 * stopped and can't be used any more, so nothing of the old process may keep running.
 *
 * @param context Opens the app's first screen again.
 * @param exit Ends the process; doesn't return in the real app.
 */
class AndroidAppProcess(
    private val context: Context,
    private val exit: () -> Unit = { Runtime.getRuntime().exit(0) },
) {

  /** Opens the app's first screen in a new process, so nothing still uses the old Firestore. */
  fun restart() {
    // The process must end whatever happens here: Firestore can't be used any more
    runCatching {
      val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
      context.startActivity(Intent.makeRestartActivityTask(launchIntent?.component))
    }
    stop()
  }

  /** Ends the process without reopening the app. */
  fun stop() = exit()
}
