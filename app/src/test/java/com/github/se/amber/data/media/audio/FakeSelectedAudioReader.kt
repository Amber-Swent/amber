// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.audio

import java.io.File

/**
 * A [SelectedAudioReader] for the ViewModel tests, with no real file picker. Each read is recorded
 * in [reads] and returns a new .m4a file in [directory] that lasts [durationMs] and contains "audio
 * from {uri}".
 *
 * To test the error cases, set [error] (e.g. an `IllegalArgumentException` for a file that isn't an
 * .m4a).
 */
class FakeSelectedAudioReader(private val directory: File, var durationMs: Long = 3_000L) :
    SelectedAudioReader {

  /** Thrown by every [read] while it is set. */
  var error: Exception? = null

  /** The URIs read so far, in order. */
  val reads: MutableList<String> = mutableListOf()

  override suspend fun read(uri: String): LocalAudio {
    reads += uri
    error?.let { throw it }
    val file = File.createTempFile("picked_", ".m4a", directory)
    file.writeText("audio from $uri")
    return LocalAudio(file, durationMs)
  }
}
