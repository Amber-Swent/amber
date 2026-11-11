// Written by Alaabrine, with assistance from Cursor (Grok).

package com.github.se.amber.data.circle

import kotlinx.coroutines.flow.Flow

/**
 * Remembers which care circle the signed-in user is working in.
 *
 * The choice is stored on this device, one entry per uid. [activeCircleId] resolves it against the
 * circles that user belongs to now: the stored id when it is still one of them, otherwise the first
 * non-blank id. Sign-out calls [clear] so the next session starts from that default again.
 */
interface ActiveCircleRepository {

  /**
   * The circle to open for [uid], given the circles they belong to now.
   *
   * Emits the stored choice when it is still in [circleIds]. Otherwise emits the first non-blank
   * id, or null when [uid] is blank or [circleIds] has no usable id. Emits again after
   * [setActiveCircle] or [clear] for this uid, so a screen that collects the flow can reload that
   * circle's media. Cancellation is rethrown.
   */
  fun activeCircleId(uid: String, circleIds: List<String>): Flow<String?>

  /**
   * Remembers [id] as the active circle for [uid].
   *
   * Does nothing when [uid] or [id] is blank. The id is kept even if it is not in the caller's
   * current circle list: [activeCircleId] falls back until the id is one of them again.
   * Cancellation is rethrown.
   */
  suspend fun setActiveCircle(uid: String, id: String)

  /**
   * Forgets [uid]'s active circle.
   *
   * Sign-out calls this with the uid that is leaving. A later [activeCircleId] for that uid uses
   * the first circle again. Another uid's choice is left as it is. A blank [uid] does nothing.
   * Cancellation is rethrown.
   */
  suspend fun clear(uid: String)
}
