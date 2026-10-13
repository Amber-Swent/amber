// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
// Modified by Neha Chakraborty and Copilot.
package com.github.se.amber.model.circle

/**
 * A universal code that lets an existing user profile join a circle. Stored at invitations/{code}:
 * the code is chosen by the app (short, easy to type) and used directly as the document id, so
 * redeeming it is one read.
 *
 * Written: created by a caregiver; usedBy is set when redeemed.
 *
 * Read: get invitations/{code} with the code the user typed. Caregiver members can also list their
 * own invitations for a selected circle. Valid if usedBy == null and expiresAt > now.
 *
 * Redeemed in one transaction: set usedBy, add the uid to CareCircle.memberIds, set
 * CareCircle.patientId to the uid if the joining UserProfile.role is PATIENT, and add circleId to
 * the existing user's UserProfile.circleIds. A transaction prevents two people from redeeming the
 * same code.
 *
 * Subject to change: the invitation flow (code format, expiry, redemption) is not final yet.
 */
data class Invitation(
    val code: String = "", // = document id; short and easy to type, e.g. 6 characters
    val circleId: String = "", // the circle the code gives access to
    val createdBy: String = "", // uid of the caregiver who created it
    val createdAt: Long = 0L, // epoch milliseconds
    val expiresAt: Long = 0L, // epoch milliseconds
    val usedBy: String? = null, // uid of whoever redeemed it; null = still valid
)
