// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

/**
 * A [MediaSender] that records its calls and answers [results] in order, then [SendResult.DONE].
 */
class FakeMediaSender(val results: MutableList<SendResult> = mutableListOf()) : MediaSender {

  /** One [send] call. */
  data class Call(val circleId: String, val mediaId: String, val lastAttempt: Boolean)

  /** Every [send] call, in order. */
  val calls = mutableListOf<Call>()

  override suspend fun send(circleId: String, mediaId: String, lastAttempt: Boolean): SendResult {
    calls += Call(circleId, mediaId, lastAttempt)
    return results.removeFirstOrNull() ?: SendResult.DONE
  }
}
