// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.media.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Plays audio files with Android's [MediaPlayer], one at a time.
 *
 * It holds a private [MediaPlayer] instead of being one, so no `android.media` type reaches a
 * ViewModel. A new [MediaPlayer] is built for each [play] and released when the file is unloaded.
 *
 * While it plays, it holds the audio focus, so other apps lower their sound or pause, and it pauses
 * itself when it loses the focus (e.g. a phone call), even if it is only asked to play lower: the
 * audios are speech, which is hard to follow under another sound. It gives the focus back when it
 * pauses, at the end of the file and on [stop]. If the focus is refused (e.g. during a call),
 * [play] loads the file without playing it, and [resume] does nothing, so the screen shows it
 * paused.
 *
 * If the file fails while playing, it is unloaded, as if [stop] was called.
 *
 * Threads: its state is used by three kinds of threads, so every access goes through one [lock].
 * The screens call it from the main thread, [play] and the position job run on [ioDispatcher], and
 * the [MediaPlayer] and the audio focus call back on the main thread. Without the lock, the
 * position job could read a player that [stop] has just released (which throws), or the end of an
 * old file could reset the state of the new one.
 *
 * The lock can't deadlock: it is the only lock in the class, and while holding it the code never
 * waits for another thread. Nothing suspends inside it (Kotlin forbids it), cancelling a job
 * doesn't wait for it to end. Collectors of [state] run in their own coroutines, and the
 * [MediaPlayer] and [AudioManager] calls don't wait for our callbacks, which are posted to the main
 * thread and simply wait their turn. A callback that calls back in on the same thread, like
 * [focusListener] calling [pause], doesn't block either, as a JVM lock can be taken again by the
 * thread that holds it. The one slow call, [MediaPlayer.prepare], runs outside the lock.
 *
 * @param context any context; only its application context is kept, for the [AudioManager].
 * @param ioDispatcher prepares the files, and updates the position while playing.
 */
class AndroidAudioPlayer(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AudioPlayer {

  private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)

  private val attributes =
      AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
          .build()

  /**
   * Pauses when another app or a call takes the audio focus, even for a while or to play lower.
   * Internal for the tests.
   */
  internal val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
    if (
        change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
    ) {
      pause()
    }
  }

  private val focusRequest =
      AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
          .setAudioAttributes(attributes)
          // tells Android it pauses instead of playing lower, so it is told to
          .setWillPauseWhenDucked(true)
          .setOnAudioFocusChangeListener(focusListener)
          .build()

  /** Updates the position while playing, for as long as the player exists. */
  private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

  /** Guards everything below (see the class documentation for why). */
  private val lock = Any()

  /** The player of the loaded file, or null when nothing is loaded. */
  private var mediaPlayer: MediaPlayer? = null

  /** The file a [play] is preparing, or null. */
  private var preparing: File? = null

  /**
   * Goes up on every [play] and on a [stop] of the file being prepared, so a [play] can tell, once
   * its file is prepared, whether it was replaced or stopped in the meantime.
   */
  private var playCount = 0

  private var positionJob: Job? = null

  private var hasFocus = false

  private val _state = MutableStateFlow(AudioState())
  override val state: StateFlow<AudioState> = _state.asStateFlow()

  /**
   * Stops what was loaded, prepares [file] and plays it.
   *
   * It takes the lock twice, as preparing reads the file and can take a while. Holding the lock
   * then would freeze every screen calling [pause] or [state] on the main thread. The first time,
   * it stops the old audio right away, so it doesn't play on while the new one is prepared, and
   * notes the file being prepared. The second time, it plays the file, unless another [play] or a
   * [stop] of this file came in between, or the caller was cancelled. The prepared file is then
   * dropped, so the last call wins, and a screen that was closed while its file was prepared
   * doesn't start playing.
   *
   * The cancelled caller needs its own check. Once the file is being prepared, the cancellation is
   * only noticed when `withContext` ends, which then throws instead of returning. So the caller
   * would never know that the file plays, and nobody would stop it.
   */
  override suspend fun play(file: File): Unit =
      withContext(ioDispatcher) {
        val count =
            synchronized(lock) {
              unload()
              preparing = file
              ++playCount
            }
        val player =
            try {
              prepare(file)
            } catch (e: IOException) {
              synchronized(lock) { endPreparing(count) }
              throw e
            }
        synchronized(lock) {
          // replaced, stopped, or its caller is gone: nobody would ever stop it
          if (!endPreparing(count) || !isActive) {
            player.release()
            return@withContext
          }
          mediaPlayer = player
          player.setOnCompletionListener { onCompletion(it) }
          player.setOnErrorListener { failed, _, _ ->
            onError(failed)
            // handled: without it, the player would report the error as the end of the file
            true
          }
          // getDuration() gives -1 when the file doesn't tell its duration
          val durationMs = player.duration.toLong().coerceAtLeast(0L)
          _state.value = AudioState(file = file, durationMs = durationMs)
          if (requestFocus()) {
            player.start()
            _state.value = _state.value.copy(isPlaying = true)
            followPosition(player)
          }
        }
      }

  /** Pauses the loaded file and gives the focus back, under the lock. */
  override fun pause() {
    synchronized(lock) {
      val player = mediaPlayer ?: return
      if (!_state.value.isPlaying) return
      player.pause()
      positionJob?.cancel()
      _state.value = _state.value.copy(isPlaying = false, positionMs = positionOf(player))
      abandonFocus()
    }
  }

  /**
   * Takes the focus and plays on from the current position, under the lock. It stays paused if the
   * focus is refused.
   */
  override fun resume() {
    synchronized(lock) {
      val player = mediaPlayer ?: return
      if (_state.value.isPlaying) return
      if (!requestFocus()) return
      player.start()
      _state.value = _state.value.copy(isPlaying = true)
      followPosition(player)
    }
  }

  /** Moves the loaded file to [positionMs], kept within the file, under the lock. */
  override fun seekTo(positionMs: Long) {
    synchronized(lock) {
      val player = mediaPlayer ?: return
      val position = positionMs.coerceIn(0L, _state.value.durationMs)
      player.seekTo(position.toInt())
      _state.value = _state.value.copy(positionMs = position)
    }
  }

  /**
   * Unloads [file] if it is loaded, or makes its [play] give up if it is being prepared, under the
   * lock. Another file is left alone.
   */
  override fun stop(file: File) {
    synchronized(lock) {
      when (file) {
        preparing -> {
          preparing = null
          playCount++
        }
        _state.value.file -> unload()
      }
    }
  }

  /**
   * Ends the preparing of the [play] numbered [count], once its file is prepared or has failed.
   * Called with the lock held.
   *
   * @return whether that [play] is still the latest one, with no [stop] of its file since. Only
   *   then is [preparing] its file, so only then is it cleared.
   */
  private fun endPreparing(count: Int): Boolean {
    val isLatest = playCount == count
    if (isLatest) preparing = null
    return isLatest
  }

  /**
   * Builds a [MediaPlayer] for [file] and prepares it. It is called without the lock, as it reads
   * the file.
   *
   * @throws IOException if the file can't be read or decoded; the player is released then.
   */
  private fun prepare(file: File): MediaPlayer {
    val player = MediaPlayer()
    try {
      player.setAudioAttributes(attributes)
      player.setDataSource(file.path)
      player.prepare()
      return player
    } catch (e: Exception) {
      player.release()
      throw e as? IOException ?: IOException("Couldn't play ${file.name}", e)
    }
  }

  /**
   * At the end of the file: back to the start, keeping the file and duration for "play again". It
   * is called on the main thread, so it takes the lock, and it ignores a player that was replaced.
   */
  private fun onCompletion(player: MediaPlayer) {
    synchronized(lock) {
      if (mediaPlayer !== player) return
      positionJob?.cancel()
      player.seekTo(0)
      _state.value = _state.value.copy(isPlaying = false, positionMs = 0L)
      abandonFocus()
    }
  }

  /**
   * Unloads the file when it fails while playing, as it can't play anymore. This also covers a
   * [pause], [resume] or [seekTo] on a player that has failed. Android doesn't throw then, and
   * reports the error here instead. It is called on the main thread, so it takes the lock, and it
   * ignores a player that was replaced.
   */
  private fun onError(player: MediaPlayer) {
    synchronized(lock) { if (mediaPlayer === player) unload() }
  }

  /**
   * Publishes the position every [POSITION_INTERVAL_MS], as long as [player] plays. Called with the
   * lock held; the job takes it on each update, as it runs on [ioDispatcher].
   */
  private fun followPosition(player: MediaPlayer) {
    positionJob?.cancel()
    positionJob = scope.launch {
      while (true) {
        delay(POSITION_INTERVAL_MS.milliseconds)
        synchronized(lock) {
          if (mediaPlayer !== player || !_state.value.isPlaying) return@launch
          _state.value = _state.value.copy(positionMs = positionOf(player))
        }
      }
    }
  }

  /** The position of [player], kept within the file. Called with the lock held. */
  private fun positionOf(player: MediaPlayer): Long =
      player.currentPosition.toLong().coerceIn(0L, _state.value.durationMs)

  /** Releases the loaded file, if any, and empties [state]. Called with the lock held. */
  private fun unload() {
    positionJob?.cancel()
    positionJob = null
    mediaPlayer?.release()
    mediaPlayer = null
    _state.value = AudioState()
    abandonFocus()
  }

  /**
   * Asks for the audio focus, unless it is held already. Called with the lock held.
   *
   * @return whether it holds the focus now.
   */
  private fun requestFocus(): Boolean {
    if (!hasFocus) {
      hasFocus =
          audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
    return hasFocus
  }

  /** Gives the audio focus back, if it is held. Called with the lock held. */
  private fun abandonFocus() {
    if (hasFocus) {
      audioManager.abandonAudioFocusRequest(focusRequest)
      hasFocus = false
    }
  }

  private companion object {
    const val POSITION_INTERVAL_MS = 200L
  }
}
