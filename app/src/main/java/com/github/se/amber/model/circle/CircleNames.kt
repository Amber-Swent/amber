// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.circle

import com.github.se.amber.model.user.Person

/**
 * Ids that can be tagged in this circle's media: its members' uids and its people's ids.
 *
 * @return Every id a [com.github.se.amber.model.media.MediaItem.shownIds] entry may contain.
 */
fun CareCircle.taggableIds(): Set<String> = memberIds.toSet() + people.keys

/**
 * Name shown to [viewerUid] for the person or member with [id], following the order documented on
 * [Person]: the viewer's own nickname for them, then their default nickname, then their first name.
 *
 * If [id] is both a key of [CareCircle.people] and of [members], the entry in [CareCircle.people]
 * is used. This should not happen, since people ids are Firestore auto-ids and members are uids.
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
  val person = people[id] ?: members[id] ?: return null
  return person.nickname?.takeIf { it.isNotBlank() } ?: person.firstName.takeIf { it.isNotBlank() }
}
