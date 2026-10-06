// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.user

/**
 * A name that can be credited in memories. Person is the single "taggable" type. Never stored as
 * its own document:
 * - for a user, it is embedded in their UserProfile (`person` field);
 * - for someone without an account, it is a value of CareCircle.people, keyed by its personId.
 *
 * A Person never becomes a user: if a credited person later creates an account, they are a new user
 * and can be re-tagged. Linking a Person to a real account is not handled yet; this may change.
 *
 * Name shown to a viewer, first match wins:
 * 1. the viewer's own nickname for this person in CareCircle.nicknames, if set and not blank;
 * 2. [nickname], if set and not blank;
 * 3. [firstName].
 */
data class Person(
    val id: String = "", // uid for a user; a Firestore auto-id (repository newId()) otherwise
    val firstName: String = "",
    val lastName: String = "",
    val nickname: String? = null, // default nickname; per-viewer ones are in CareCircle.nicknames
)
