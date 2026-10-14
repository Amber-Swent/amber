// This file was written by Neha Chakraborty and Copilot
package com.github.se.amber.data.invitation

/** Invitation information displayed by the caregiver's invitation-code screen. */
data class InvitationSummary(
    val code: String,
    val createdAt: Long,
    val expiresAt: Long,
    val status: InvitationStatus,
)

/** Whether an invitation can still be shared. */
enum class InvitationStatus {
  ACTIVE,
  EXPIRED,
  USED,
}
