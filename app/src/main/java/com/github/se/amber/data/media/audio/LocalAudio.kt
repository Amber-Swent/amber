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
 * @property waveform how loud the audio is along its length, for `MediaItem.Audio.waveform`, so the
 *   listening screen can draw it. It holds [WAVEFORM_BARS] values from 0 (silence) to 1 (the
 *   loudest), in playback order, each the loudest point of its part of the audio. It is empty when
 *   it isn't known (e.g. for a picked file), and the screen then shows a plain progress bar.
 */
data class LocalAudio(
    val file: File,
    val durationMs: Long,
    val waveform: List<Float> = emptyList(),
)

/** How many values a known [LocalAudio.waveform] has; the screen draws one bar per value. */
const val WAVEFORM_BARS = 64
