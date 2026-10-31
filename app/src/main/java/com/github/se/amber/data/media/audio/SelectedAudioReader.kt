// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.audio

import com.github.se.amber.data.media.MAX_FILE_BYTES

/**
 * Reads a voice memo the user picked from the phone's files, the audio counterpart of
 * `SelectedPictureReader`. ViewModels receive it as a constructor parameter, so tests can pass a
 * fake.
 */
interface SelectedAudioReader {

  /**
   * Copies the audio at [uri] (a content URI from the system file picker) into a new .m4a file in
   * the app's cache, and reads its duration.
   *
   * @throws IllegalArgumentException if it isn't an .m4a (`audio/mp4`), can't be read, has no
   *   duration or is over [MAX_FILE_BYTES]; no file is left behind then.
   */
  suspend fun read(uri: String): LocalAudio
}
