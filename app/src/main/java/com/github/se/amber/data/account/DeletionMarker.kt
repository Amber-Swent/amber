// Written by Neha Chakraborty, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.account

import android.content.Context
import androidx.core.content.edit

/**
 * Remembers, even if the app dies, that an account deletion started and this device may still hold
 * the user's data. It says nothing about the backend: the account can still exist.
 */
interface DeletionMarker {
  val isSet: Boolean

  fun set()

  fun remove()
}

/** Keeps the marker in its own preferences, on disk before [set] or [remove] returns. */
class SharedPreferencesDeletionMarker(context: Context) : DeletionMarker {
  private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  override val isSet: Boolean
    get() = preferences.getBoolean(KEY, false)

  // commit: the process can end right after these, which would lose an asynchronous write
  override fun set() = preferences.edit(commit = true) { putBoolean(KEY, true) }

  override fun remove() = preferences.edit(commit = true) { remove(KEY) }

  private companion object {
    const val PREFERENCES_NAME = "account_deletion"
    const val KEY = "deletion_started"
  }
}
