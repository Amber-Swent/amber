// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.audio

import com.google.firebase.Firebase
import com.google.firebase.app

/**
 * Holds the app's single [AudioPlayer], since the phone has a single speaker: it is what makes
 * playing an audio stop any other, whichever screen started it.
 *
 * ViewModels should receive the player as a constructor parameter (their factory reads it here), so
 * tests can pass a fake instead, and ViewModels never import Firebase.
 */
object AudioPlayerProvider {

  /**
   * The app's player, built on first use. The app's context comes from Firebase, which the app
   * initializes before any activity starts; if building fails, the next use tries again.
   */
  val player: AudioPlayer by lazy { AndroidAudioPlayer(Firebase.app.applicationContext) }
}
