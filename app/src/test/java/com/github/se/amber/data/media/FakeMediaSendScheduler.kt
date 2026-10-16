// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

/**
 * A [MediaSendScheduler] that runs nothing and records its calls in [calls], as "schedule
 * {circleId}/{mediaId}", "reschedule {circleId}/{mediaId}", "cancel {circleId}/{mediaId}",
 * "cancelCircle {circleId}" or "cancelAll". [schedule] also runs [onSchedule], then throws
 * [scheduleError] while it is set (after recording the call).
 */
class FakeMediaSendScheduler : MediaSendScheduler {

  /** Every call, in order. */
  val calls = mutableListOf<String>()

  /** Thrown by every [schedule] while set. */
  var scheduleError: Exception? = null

  /** Runs during every [schedule], with its circleId and mediaId: what was done before it. */
  var onSchedule: (circleId: String, mediaId: String) -> Unit = { _, _ -> }

  override fun schedule(circleId: String, mediaId: String) {
    calls += "schedule $circleId/$mediaId"
    onSchedule(circleId, mediaId)
    scheduleError?.let { throw it }
  }

  override fun reschedule(circleId: String, mediaId: String) {
    calls += "reschedule $circleId/$mediaId"
  }

  override fun cancel(circleId: String, mediaId: String) {
    calls += "cancel $circleId/$mediaId"
  }

  override fun cancelCircle(circleId: String) {
    calls += "cancelCircle $circleId"
  }

  override fun cancelAll() {
    calls += "cancelAll"
  }
}
