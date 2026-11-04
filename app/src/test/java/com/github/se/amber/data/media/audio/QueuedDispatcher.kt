// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.audio

import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher

/**
 * A dispatcher that runs nothing until [runAll] is called, so a test can act between the moment a
 * class's work is done and the moment its caller resumes (e.g. cancel the caller then).
 */
class QueuedDispatcher : CoroutineDispatcher() {

  /** Thread-safe, as a coroutine that waits (`delay`) here is resumed from another thread. */
  private val queue = ConcurrentLinkedQueue<Runnable>()

  override fun dispatch(context: CoroutineContext, block: Runnable) {
    queue.add(block)
  }

  /** Runs everything queued, including what the queued work queues itself. */
  fun runAll() {
    while (true) queue.poll()?.run() ?: return
  }
}
