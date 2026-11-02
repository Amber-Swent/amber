// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import com.github.se.amber.data.media.MAX_FILE_BYTES
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Records audio from the microphone with Android's [MediaRecorder], into .m4a files (AAC, mono,
 * 44.1 kHz, 64 kbps: about 0.5 MB per minute) in an `audio/recorded` folder of the app's cache.
 *
 * It holds a private [MediaRecorder] instead of being one, so no `android.media` type reaches a
 * ViewModel. A new [MediaRecorder] is built for each recording and released after it.
 *
 * Leftovers: the first [start] after the app starts empties the folder, to delete the files of
 * recordings the app couldn't finish (e.g. it was killed while recording). Later starts don't, so a
 * recording returned by [stop] stays until the caller uploads it (which moves the file elsewhere)
 * or deletes it. If it is still in the folder when the app starts again, the upload never happened,
 * and the first [start] then deletes it with the other leftovers.
 *
 * Threads: its state is used by three kinds of threads, so every access goes through one [lock].
 * The screens call [cancel] from the main thread, [start] and [stop] run on [ioDispatcher], and so
 * does the amplitude job, while the [MediaRecorder] reports a limit on the main thread. Without the
 * lock, the amplitude job could read a recorder that [stop] has just released (which throws), or a
 * limit reported late could end the next recording.
 *
 * The lock can't deadlock: it is the only lock in the class, and while holding it the code never
 * waits for another thread. Nothing suspends inside it (Kotlin forbids it), cancelling a job
 * doesn't wait for it to end, collectors of the flows run in their own coroutines, and the
 * [MediaRecorder] calls don't wait for our callback, which is posted to the main thread and simply
 * waits its turn.
 *
 * @param context any context; only its application context is kept.
 * @param ioDispatcher runs the file and recorder work, and reads the [amplitude].
 * @param createMediaRecorder builds the [MediaRecorder] of each recording. Only tests change it, to
 *   pass a mock, as Robolectric's recorder records nothing.
 * @param clock gives the time in milliseconds, to measure the duration. Only tests change it.
 */
class AndroidAudioRecorder(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val createMediaRecorder: () -> MediaRecorder = { newMediaRecorder(context) },
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : AudioRecorder {

  private val appContext = context.applicationContext
  private val directory = File(appContext.cacheDir, RECORDED_DIR)

  /** Reads the [amplitude] while recording, for as long as the recorder exists. */
  private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

  /** Guards everything below (see the class documentation for why). */
  private val lock = Any()

  /** The recorder of the current recording, or of the one that stopped at a limit. */
  private var mediaRecorder: MediaRecorder? = null

  /** The current recording's file, or null when there is no recording to return. */
  private var file: File? = null

  /** When the current recording started, from [clock]. */
  private var startedAt = 0L

  /** When the current recording stopped at a limit, or null while it goes on. */
  private var stoppedAt: Long? = null

  /** Every [amplitude] read during the current recording, to build its waveform. */
  private val samples = mutableListOf<Float>()

  private var amplitudeJob: Job? = null

  private var leftoversDeleted = false

  private val _isRecording = MutableStateFlow(false)
  override val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

  private val _amplitude = MutableStateFlow(0f)
  override val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

  /**
   * Checks the permission, then builds and starts a new [MediaRecorder] into a new file, under the
   * lock. The recorder and the file are only kept once it has started, so a failure leaves nothing.
   */
  override suspend fun start(): Unit =
      withContext(ioDispatcher) {
        if (
            appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
        ) {
          throw SecurityException("The microphone permission isn't granted")
        }
        synchronized(lock) {
          check(file == null) { "A recording is in progress or waiting to be taken with stop()" }
          deleteLeftovers()
          // built before the file, so a failure here leaves no file behind
          val recorder = openingMicrophone { createMediaRecorder() }
          val output =
              try {
                File.createTempFile("recorded_", ".m4a", directory)
              } catch (e: IOException) {
                recorder.release()
                throw e
              }
          try {
            openingMicrophone {
              configure(recorder, output)
              recorder.prepare()
              recorder.start()
            }
          } catch (e: Exception) {
            recorder.release()
            output.delete() // NOSONAR: a leftover is removed at the next start
            throw e
          }
          mediaRecorder = recorder
          file = output
          startedAt = clock()
          stoppedAt = null
          samples.clear()
          _isRecording.value = true
          amplitudeJob = scope.launch { readAmplitude(recorder) }
        }
      }

  /**
   * Stops the recording on [ioDispatcher] (see [stopNow]). If the caller is cancelled meanwhile,
   * `withContext` throws instead of returning the recording, so nobody would ever take its file: it
   * is deleted then.
   */
  override suspend fun stop(): LocalAudio {
    var audio: LocalAudio? = null
    try {
      return withContext(ioDispatcher) { stopNow().also { audio = it } }
    } catch (e: CancellationException) {
      audio?.file?.delete() // NOSONAR: a leftover is removed at the next start
      throw e
    }
  }

  /**
   * Stops and releases the recorder, and returns the recording with its duration and waveform,
   * under the lock. The state is cleared first, so the recorder can start again even if this fails.
   */
  private fun stopNow(): LocalAudio =
      synchronized(lock) {
        val output = checkNotNull(file) { "There is no recording to return" }
        // kept, as clear() forgets it
        val recorder = checkNotNull(mediaRecorder)
        val reachedLimit = stoppedAt != null
        val durationMs = (stoppedAt ?: clock()) - startedAt
        val waveform = buildWaveform(samples)
        clear()
        try {
          recorder.stop()
        } catch (e: RuntimeException) {
          // after a limit, the recorder may have stopped by itself already, and the file is
          // complete; otherwise, nothing was recorded
          if (!reachedLimit) {
            output.delete() // NOSONAR: a leftover is removed at the next start
            throw IllegalStateException("The recording is empty or too short to be kept", e)
          }
        } finally {
          recorder.release()
        }
        if (output.length() == 0L) {
          output.delete() // NOSONAR: a leftover is removed at the next start
          throw IllegalStateException("The recording is empty")
        }
        LocalAudio(output, durationMs, waveform)
      }

  /** Stops and releases the recorder, if any, and deletes its file, under the lock. */
  override fun cancel() {
    synchronized(lock) {
      val output = file ?: return
      // kept, as clear() forgets it; there is always one with a file
      val recorder = checkNotNull(mediaRecorder)
      clear()
      try {
        recorder.stop()
      } catch (_: RuntimeException) {
        // nothing recorded, or already stopped at a limit: the file is deleted anyway
      } finally {
        recorder.release()
      }
      output.delete() // NOSONAR: a leftover is removed at the next start
    }
  }

  /**
   * Runs [block], turning its errors into the ones [start] may throw: a [SecurityException] or an
   * [IOException] stays as it is, and anything else means the microphone couldn't be opened.
   */
  private inline fun <T> openingMicrophone(block: () -> T): T =
      try {
        block()
      } catch (e: IOException) {
        throw e
      } catch (e: SecurityException) {
        throw e
      } catch (e: Exception) {
        throw IOException("Couldn't open the microphone", e)
      }

  /** Sets the format, the limits and the output file of [recorder], and listens for the limits. */
  private fun configure(recorder: MediaRecorder, output: File) {
    recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
    recorder.setAudioChannels(MONO)
    recorder.setAudioSamplingRate(SAMPLING_RATE_HZ)
    recorder.setAudioEncodingBitRate(BIT_RATE)
    recorder.setMaxDuration(MAX_RECORDING_MS.toInt())
    // the MPEG-4 trailer is written after the limit, so it stops a little before it
    recorder.setMaxFileSize(MAX_FILE_BYTES - FILE_SIZE_MARGIN_BYTES)
    recorder.setOutputFile(output.path)
    recorder.setOnInfoListener { _, what, _ ->
      if (
          what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
              what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED
      ) {
        onLimitReached(recorder)
      }
    }
  }

  /**
   * Ends the recording without returning it, so the screen can take it with [stop]. It is called on
   * the main thread, so it takes the lock, and it ignores a recorder that was replaced.
   */
  private fun onLimitReached(recorder: MediaRecorder) {
    synchronized(lock) {
      if (mediaRecorder !== recorder || stoppedAt != null) return
      stoppedAt = clock()
      amplitudeJob?.cancel()
      _isRecording.value = false
      _amplitude.value = 0f
    }
  }

  /**
   * Reads the microphone level every [AMPLITUDE_INTERVAL_MS], as long as [recorder] records. It
   * runs on [ioDispatcher], so it takes the lock on each read.
   */
  private suspend fun readAmplitude(recorder: MediaRecorder) {
    while (true) {
      delay(AMPLITUDE_INTERVAL_MS.milliseconds)
      synchronized(lock) {
        if (mediaRecorder !== recorder || stoppedAt != null) return
        // the loudest negative sample, -32768, would give a little over 1
        val level = (recorder.maxAmplitude / MAX_AMPLITUDE).coerceAtMost(1f)
        samples += level
        _amplitude.value = level
      }
    }
  }

  /**
   * Forgets the current recording, without touching its recorder or its file. Called with the lock
   * held.
   */
  private fun clear() {
    amplitudeJob?.cancel()
    amplitudeJob = null
    mediaRecorder = null
    file = null
    stoppedAt = null
    samples.clear()
    _isRecording.value = false
    _amplitude.value = 0f
  }

  /**
   * Empties the folder on the first call only (see the class documentation), and creates it. Called
   * with the lock held.
   */
  private fun deleteLeftovers() {
    if (!leftoversDeleted) {
      directory.listFiles()?.forEach { it.delete() } // NOSONAR: retried at the next start
      leftoversDeleted = true
    }
    directory.mkdirs()
  }

  private companion object {
    const val RECORDED_DIR = "audio/recorded"
    const val MONO = 1
    const val SAMPLING_RATE_HZ = 44_100
    const val BIT_RATE = 64_000
    const val FILE_SIZE_MARGIN_BYTES = 256L * 1024
    const val AMPLITUDE_INTERVAL_MS = 100L

    /** The highest value [MediaRecorder.getMaxAmplitude] returns. */
    const val MAX_AMPLITUDE = 32_767f

    /** Builds a [MediaRecorder] with the constructor of this Android version. */
    fun newMediaRecorder(context: Context): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          MediaRecorder(context.applicationContext)
        } else {
          @Suppress("DEPRECATION") MediaRecorder()
        }
  }
}

/**
 * Turns the levels read while recording into a waveform of exactly [WAVEFORM_BARS] values from 0 to
 * 1, in playback order. The samples are split into [WAVEFORM_BARS] equal parts, and each bar is the
 * loudest sample of its part, as `LocalAudio.waveform` promises: an average would flatten a word
 * said in a mostly silent part, and more so the longer the recording. With fewer samples than bars,
 * each sample covers several bars; with none, every bar is 0.
 */
internal fun buildWaveform(samples: List<Float>): List<Float> {
  if (samples.isEmpty()) return List(WAVEFORM_BARS) { 0f }
  return List(WAVEFORM_BARS) { bar ->
    val from = bar * samples.size / WAVEFORM_BARS
    val to = maxOf(from + 1, (bar + 1) * samples.size / WAVEFORM_BARS)
    samples.subList(from, to).max()
  }
}
