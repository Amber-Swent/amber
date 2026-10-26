// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.circle

import com.github.se.amber.model.user.Person

/** The person's non-blank first and last names, separated by a space. */
fun Person.fullName(): String =
    listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")

/**
 * Ids that can be tagged in this circle's media: its members' uids and its people's ids.
 *
 * @return Every id a [com.github.se.amber.model.media.MediaItem.shownIds] entry may contain.
 */
fun CareCircle.taggableIds(): Set<String> = memberIds.toSet() + people.keys

/**
 * Name shown to [viewerUid] for the person or member with [id], following the order documented on
 * [Person]: the viewer's own nickname for them, then their default nickname, then their full name.
 *
 * @param id A member uid or a key of [CareCircle.people].
 * @param viewerUid The uid of the user looking at the name, or null if unknown.
 * @param members The members' [Person]s (from their profiles), keyed by uid.
 * @return The name to display, or null if nothing is known about [id].
 */
fun CareCircle.displayNameOf(
    id: String,
    viewerUid: String?,
    members: Map<String, Person>,
): String? {
  val viewerNickname = viewerUid?.let { nicknames[it]?.get(id) }
  if (!viewerNickname.isNullOrBlank()) return viewerNickname
  val person =
      when (id) {
        in people -> people.getValue(id)
        in members -> members.getValue(id)
        else -> return null
      }
  return person.nickname?.takeIf { it.isNotBlank() } ?: person.fullName().takeIf { it.isNotBlank() }
}
