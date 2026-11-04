// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.audio

import com.google.firebase.Firebase
import com.google.firebase.app

/**
 * Holds the app's [SelectedAudioReader]. It keeps no state between reads, so the whole app can
 * share it.
 *
 * ViewModels should receive the reader as a constructor parameter (their factory reads it here), so
 * tests can pass a fake instead, and ViewModels never import Firebase.
 */
object SelectedAudioReaderProvider {

  /**
   * The app's reader, built on first use. The app's context comes from Firebase, which the app
   * initializes before any activity starts; if building fails, the next use tries again.
   */
  val reader: SelectedAudioReader by lazy {
    AndroidSelectedAudioReader(Firebase.app.applicationContext)
  }
}
