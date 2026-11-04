// Written by Alaabrine, with assistance from Cursor.
package com.github.se.amber.ui.offline

import com.github.se.amber.data.circle.CareCircleError
import com.github.se.amber.data.profile.ProfileReadError

/**
 * Shown when a screen already has something loaded and a later read fails because of the network.
 */
const val OFFLINE_SHOWING_SAVED = "You're offline. Showing what was saved on this phone."

/** Shown when nothing could be loaded because the phone is offline. */
const val OFFLINE_TRY_AGAIN = "You're offline. Check your connection and try again."

/**
 * [fallback] for any failure that is not a known network error. A network error uses
 * [OFFLINE_SHOWING_SAVED] when [showingSaved] is true, and [OFFLINE_TRY_AGAIN] otherwise.
 */
fun Throwable.toLoadErrorMessage(fallback: String, showingSaved: Boolean): String =
    when (this) {
      is CareCircleError.Network,
      is ProfileReadError.Network -> if (showingSaved) OFFLINE_SHOWING_SAVED else OFFLINE_TRY_AGAIN
      else -> fallback
    }
