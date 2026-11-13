// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver.upload.audios

import android.Manifest
import android.app.Activity
import android.content.Context
import androidx.core.app.ActivityCompat
import androidx.core.content.edit

private const val MICROPHONE_PREFS = "microphone_permission"
private const val MICROPHONE_ASKED_KEY = "asked"

/**
 * Whether the Android microphone popup was already requested by this app. Android does not tell
 * "never asked" from "refused twice" (both look like "no rationale"), so the app remembers it.
 */
fun Context.hasAskedMicrophone(): Boolean =
    getSharedPreferences(MICROPHONE_PREFS, Context.MODE_PRIVATE)
        .getBoolean(MICROPHONE_ASKED_KEY, false)

/** Remembers that the Android microphone popup was requested; see [hasAskedMicrophone]. */
fun Context.markMicrophoneAsked() {
  getSharedPreferences(MICROPHONE_PREFS, Context.MODE_PRIVATE).edit {
    putBoolean(MICROPHONE_ASKED_KEY, true)
  }
}

/**
 * Whether Android would still show its microphone popup: it was never requested, or it was refused
 * once (Android then offers a rationale). After two refusals Android shows nothing anymore.
 *
 * @param activity the activity asking for the permission, or null if there is none.
 */
fun Context.androidWillAskMicrophone(activity: Activity?): Boolean =
    !hasAskedMicrophone() ||
        // shouldShowRequestPermissionRationale returns false before the first request, true after
        // one refusal, and false again after two refusals (Android then shows no popup anymore).
        // Alone, it cannot tell "never asked" from "refused twice": that is why hasAskedMicrophone
        // is checked first. Once the app has asked, false means Android will not show its popup.
        (activity?.let {
          ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO)
        } ?: true)
