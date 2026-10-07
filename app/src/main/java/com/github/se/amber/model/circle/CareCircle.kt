// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code and GitHub Copilot.
package com.github.se.amber.model.circle

import com.github.se.amber.model.location.Place
import com.github.se.amber.model.user.Person

/** viewerUid -> (personId or uid -> name): the names each member gives the people in the circle. */
typealias ViewerNicknames = Map<String, Map<String, String>>

/**
 * A patient and everyone around them. Stored at careCircles/{id}. Small enough to hold everything
 * circle-wide in one document, so loading a circle is a single read.
 *
 * Ids (circleId, the personIds in [people] and the placeIds in [places]) are Firestore auto-ids
 * generated on the device with collection.document().id (no network call, works offline). Only the
 * repository can call it, so it exposes `fun newId(): String`.
 *
 * Written:
 * - created by a caregiver, at onboarding or later (memberIds = [their uid], patientId = ""); its
 *   id is added to the creator's UserProfile.circleIds at the same time.
 * - memberIds (and patientId for a PATIENT code): only when an invitation is redeemed, together
 *   with the Invitation and the user's UserProfile.circleIds, so they always stay consistent.
 *   Members are added one at a time, never by rewriting the whole list.
 * - people: one entry added, changed or removed at a time, never the whole map, so concurrent edits
 *   by two members don't overwrite each other's entries.
 * - nicknames: each member edits only their own entry (keyed by their uid).
 * - places: by caregivers only, one entry added, changed or removed at a time, like people.
 *
 * Read: after login, get careCircles/{id} for each id in UserProfile.circleIds, then keep a
 * snapshot listener on the circle being viewed so the people list updates live.
 *
 * Rules: [memberIds] is the source of truth for membership. Members may only change `name`,
 * `people` and their own entry in `nicknames`, and caregivers (UserProfile.role) also `places`;
 * `patientId` is set once, when the patient's phone is linked.
 */
data class CareCircle(
    val id: String = "", // circleId = document id
    val name: String = "", // "Arthur's family"
    val patientId: String = "", // the patient's uid; "" until their phone redeems a PATIENT code
    // uids of all members incl. the patient, no duplicates (written with arrayUnion/arrayRemove);
    // security rules check membership against it
    val memberIds: List<String> = emptyList(),
    val people: Map<String, Person> = emptyMap(), // personId -> Person without an account
    val nicknames: ViewerNicknames = emptyMap(),
    // placeId -> place the patient often goes to; can be set up before the patient joins
    val places: Map<String, Place> = emptyMap(),
    val createdBy: String = "", // uid of the caregiver who created the circle
    val createdAt: Long = 0L, // Optional; epoch milliseconds
    // Assigned by the bucket-provisioning backend; empty until the bucket is ready.
    val storageBucket: String = "",
)
