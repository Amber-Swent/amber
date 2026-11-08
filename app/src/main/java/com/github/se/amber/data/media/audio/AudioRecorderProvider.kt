// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.media.audio

import com.google.firebase.Firebase
import com.google.firebase.app

/**
 * Holds the app's single [AudioRecorder], since the phone has a single microphone: a second
 * [AudioRecorder.start] while a recording is in progress fails, whichever screen calls it.
 *
 * ViewModels should receive the recorder as a constructor parameter (their factory reads it here),
 * so tests can pass a fake instead, and ViewModels never import Firebase.
 */
object AudioRecorderProvider {

  /**
   * The app's recorder, built on first use. The app's context comes from Firebase, which the app
   * initializes before any activity starts; if building fails, the next use tries again.
   */
  val recorder: AudioRecorder by lazy { AndroidAudioRecorder(Firebase.app.applicationContext) }
}
