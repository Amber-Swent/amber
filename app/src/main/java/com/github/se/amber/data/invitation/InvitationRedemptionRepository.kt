// This file was written by Neha Chakraborty and Copilot, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.invitation

/** Redeems an invitation for the currently authenticated user. */
fun interface InvitationRedemptionRepository {

  /**
   * Redeems [code] and adds the current user to its care circle.
   *
   * The callable backend derives the user's role from their persisted profile. Callers must not
   * supply a role or personal details.
   */
  suspend fun redeemInvitation(code: String): Result<InvitationRedemptionResult>
}
