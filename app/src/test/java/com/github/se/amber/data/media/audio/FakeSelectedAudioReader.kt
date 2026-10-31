// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.audio

import java.io.File

/**
 * A [SelectedAudioReader] without a content resolver, for the ViewModel tests. Records each URI
 * asked for in [reads], and returns a new .m4a file in [directory] containing "audio from {uri}"
 * that lasts [durationMs]; it throws [error] instead while it is set (e.g. an
 * `IllegalArgumentException` for a file that isn't an .m4a).
 */
class FakeSelectedAudioReader(private val directory: File, var durationMs: Long = 3_000L) :
    SelectedAudioReader {

  /** Thrown by every [read] while set. */
  var error: Exception? = null

  val reads: MutableList<String> = mutableListOf()

  override suspend fun read(uri: String): LocalAudio {
    reads += uri
    error?.let { throw it }
    val file = File.createTempFile("picked_", ".m4a", directory)
    file.writeText("audio from $uri")
    return LocalAudio(file, durationMs)
  }
}
