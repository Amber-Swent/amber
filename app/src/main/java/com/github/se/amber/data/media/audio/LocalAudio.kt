// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.audio

import java.io.File

/**
 * An audio file that is on the phone but not uploaded yet. It was either recorded with
 * [AudioRecorder] or picked with [SelectedAudioReader].
 *
 * Its .m4a [file] is in the app's cache, ready to be uploaded with `MediaRepository.addMedia` (with
 * a `storagePath` ending in .m4a). `MediaRepository.addMedia` moves the file, so don't use it
 * afterwards. To throw the audio away instead (e.g. the user wants to record again), delete [file].
 *
 * @property file the .m4a file that holds the audio.
 * @property durationMs how long it lasts, for `MediaItem.Audio.durationMs`.
 */
data class LocalAudio(val file: File, val durationMs: Long)
