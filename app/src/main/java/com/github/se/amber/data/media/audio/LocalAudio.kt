// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.audio

import java.io.File

/**
 * A voice memo on the phone, not uploaded yet: recorded by [AudioRecorder] or picked through
 * [SelectedAudioReader]. Its .m4a [file] is in the app's cache, ready for
 * `MediaRepository.addMedia` (with a `storagePath` ending in .m4a), which moves it: don't use it
 * afterwards.
 *
 * @property durationMs how long it lasts, for `MediaItem.Audio.durationMs`.
 */
data class LocalAudio(val file: File, val durationMs: Long)
