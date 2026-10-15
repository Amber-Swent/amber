// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

/**
 * A [MediaSender] that records its calls and answers [results] in order, then [SendResult.DONE].
 * [beforeAnswer], if set, runs during each call, just before it answers: the moment its job is
 * still running although the send has decided its result.
 */
class FakeMediaSender(val results: MutableList<SendResult> = mutableListOf()) : MediaSender {

  /** One [send] call. */
  data class Call(val circleId: String, val mediaId: String)

  /** Every [send] call, in order. */
  val calls = mutableListOf<Call>()

  /** Runs during each [send], just before it answers. */
  var beforeAnswer: () -> Unit = {}

  override suspend fun send(circleId: String, mediaId: String): SendResult {
    calls += Call(circleId, mediaId)
    val result = results.removeFirstOrNull() ?: SendResult.DONE
    beforeAnswer()
    return result
  }
}
