// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.audio

import com.github.se.amber.data.media.MAX_FILE_BYTES

/**
 * Reads an audio file that the user picked from the phone's files. It is the audio counterpart of
 * `SelectedPictureReader`.
 *
 * ViewModels receive it as a constructor parameter, so their tests can use a fake instead.
 */
fun interface SelectedAudioReader {

  /**
   * Copies the picked audio into a new .m4a file in the app's cache, and reads how long it lasts.
   *
   * @param uri the content URI of the audio, as given by the system file picker.
   * @return the copied audio. Its file now belongs to the caller.
   * @throws IllegalArgumentException if the audio isn't an .m4a (`audio/mp4`), can't be read, has
   *   no duration, or is bigger than [MAX_FILE_BYTES]. No file is left behind in that case.
   */
  suspend fun read(uri: String): LocalAudio
}
