// This file was written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude
// Code.
package com.github.se.amber.data.invitation

/**
 * Holds the app's single [InvitationRedemptionRepository], built lazily on first access.
 *
 * ViewModels should receive the repository as a constructor parameter (their factory reads it
 * here), so tests can pass a fake instead. The default Firebase app must be initialized before
 * [repository] is first accessed; if building fails, the next access tries again.
 */
object InvitationRedemptionRepositoryProvider {
  val repository: InvitationRedemptionRepository by lazy {
    FirebaseInvitationRedemptionRepository()
  }
}
