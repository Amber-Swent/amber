// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.circle

import com.github.se.amber.model.user.Role

/**
 * A code that lets someone join a circle, or links the patient's phone (role = PATIENT). Stored at
 * invitations/{code}: the code is chosen by the app (short, easy to type) and used directly as the
 * document id, so redeeming it is one read.
 *
 * Written: created by a caregiver; usedBy is set when redeemed.
 *
 * Read: get invitations/{code} with the code the user typed (rules allow get, not list, so codes
 * can't be enumerated). Valid if usedBy == null and expiresAt > now.
 *
 * Redeemed in one transaction: set usedBy, add the uid to CareCircle.memberIds, set
 * CareCircle.patientId to the uid if role == PATIENT, and add circleId to the user's
 * UserProfile.circleIds (creating the profile, with the invitation's role, if this is their first
 * circle). A transaction prevents two people from redeeming the same code.
 *
 * Subject to change: the invitation flow (code format, expiry, redemption) is not final yet.
 */
data class Invitation(
    val code: String = "", // = document id; short and easy to type, e.g. 6 characters
    val circleId: String = "", // the circle the code gives access to
    val role: Role = Role.CAREGIVER, // role the person joining will get
    val createdBy: String = "", // uid of the caregiver who created it
    val createdAt: Long = 0L, // epoch milliseconds
    val expiresAt: Long = 0L, // epoch milliseconds
    val usedBy: String? = null, // uid of whoever redeemed it; null = still valid
)
