// This file was written by Neha Chakraborty and Copilot
package com.github.se.amber.data.invitation

import com.github.se.amber.model.user.Role

/** The care circle and persisted role returned after a successful invitation redemption. */
data class InvitationRedemptionResult(
    val careCircleId: String,
    val role: Role,
)
