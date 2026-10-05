// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.user

/**
 * One per Firebase Auth account. Stored at users/{uid}. The uid identifies a user everywhere
 * (memberIds, authorId, createdBy, usedBy, shownIds).
 *
 * Written: once at onboarding (creating or joining a first circle) with set(); afterwards only by
 * the user themselves (name, nickname), and [circleIds] gains an entry each time they create or
 * join another circle.
 *
 * Read:
 * - at login: get users/{uid} with the signed-in uid. Missing document = first launch ->
 *   onboarding.
 * - all members of a circle: query users whose circleIds contain the circle's id (one query, used
 *   for names in the UI and the people screen).
 *
 * Rules: readable by the user and by members of any circle they share (checked against
 * CareCircle.memberIds).
 *
 * A user can belong to several circles. [circleIds] lets the app list them with a single read;
 * membership itself is CareCircle.memberIds, kept in sync with circleIds when a circle is created
 * or an invitation is redeemed.
 *
 * Subject to change: [role] is global, the same in every circle the user belongs to. Someone who is
 * a caregiver in one circle can't be the patient of another, and redeeming an invitation whose role
 * differs from the user's current role has no defined outcome yet. The role may have to move to a
 * per-circle value.
 */
data class UserProfile(
    val uid: String = "", // Firebase Auth uid = document id
    val role: Role = Role.CAREGIVER,
    // this user's name and nickname, embedded; person.id == uid, always
    val person: Person = Person(),
    // ids of the circles they belong to, no duplicates (written with arrayUnion); empty until they
    // create or join one
    val circleIds: List<String> = emptyList(),
)
