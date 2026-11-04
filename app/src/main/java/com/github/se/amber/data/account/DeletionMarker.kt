// Written by Neha Chakraborty, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.account

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException

/**
 * Remembers, even if the app dies, that an account deletion started and this device may still hold
 * the user's data. It says nothing about the backend: the account can still exist.
 */
interface DeletionMarker {
  val isSet: Boolean

  /** @throws IOException if the marker couldn't be saved: a later launch wouldn't see it. */
  fun set()

  /** @throws IOException if the marker couldn't be removed: a later launch would still see it. */
  fun remove()
}

/** Keeps the marker in its own preferences, on disk before [set] or [remove] returns. */
class SharedPreferencesDeletionMarker(context: Context) : DeletionMarker {
  private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  override val isSet: Boolean
    get() = preferences.getBoolean(KEY, false)

  override fun set() = save(preferences.edit().putBoolean(KEY, true))

  override fun remove() = save(preferences.edit().remove(KEY))

  // commit: the process can end right after these, which would lose an asynchronous write
  private fun save(editor: SharedPreferences.Editor) {
    if (!editor.commit()) throw IOException("The deletion marker could not be saved")
  }

  private companion object {
    const val PREFERENCES_NAME = "account_deletion"
    const val KEY = "deletion_started"
  }
}
