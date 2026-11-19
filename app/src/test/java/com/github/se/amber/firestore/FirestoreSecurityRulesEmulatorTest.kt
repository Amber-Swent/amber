// Written by Alaabrine, with assistance from Cursor (Composer).
// Modified by Neha Chakraborty and Copilot.
// Modified by Neha Chakraborty and Claude Code.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Alaabrine, with assistance from Cursor (Grok).
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by Alaabrine, with assistance from Cursor Agent.

package com.github.se.amber.firestore

import com.github.se.amber.data.invitation.universalInvitationPayload
import com.github.se.amber.emulator.FirestoreEmulatorClient
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

/**
 * Emulator checks against the real `firebase/firestore/firestore.rules` (not the Kotlin mirror).
 *
 * Talks to the Firestore emulator over REST with unsigned JWTs so each call can act as any uid.
 * Requires the Firestore emulator from `firebase.json`. Example: `firebase emulators:exec --only
 * firestore "./gradlew :app:testDebugUnitTest --tests '*FirestoreSecurityRulesEmulatorTest'"`
 */
class FirestoreSecurityRulesEmulatorTest {

  private val fs = FirestoreEmulatorClient()

  @Before
  fun clearData() {
    assumeTrue("Firestore emulator is not running on port 8080", emulatorReachable())
    fs.clear()
  }

  @Test
  fun onboardingBatch_inviteCreateAndCrossUserReads_enforcedByEmulator() {
    val circleId = "c-onboard"
    val care = "care"
    val peer = "peer"
    val stranger = "stranger"

    // Only the backend assigns a circle's Storage bucket: onboarding can't pick one.
    fs.assertFails(
        care,
        fs.commit(
            fs.setWrite("users/$care", user(care, "CAREGIVER", listOf(circleId), "Ada")),
            fs.setWrite(
                "careCircles/$circleId",
                circle(circleId, care, listOf(care), patientId = "") +
                    ("storageBucket" to "other-family-bucket"),
            ),
        ),
    )
    // Caregiver onboarding: profile + first circle in one commit (isCaregiverAfter / getAfter).
    fs.assertSucceeds(
        care,
        fs.commit(
            fs.setWrite("users/$care", user(care, "CAREGIVER", listOf(circleId), "Ada")),
            fs.setWrite(
                "careCircles/$circleId",
                circle(circleId, care, listOf(care), patientId = ""),
            ),
        ),
    )
    fs.assertSucceeds(care, fs.get("careCircles/$circleId"))
    fs.assertSucceeds(
        care,
        fs.set(
            "invitations/JOIN01",
            invite("JOIN01", circleId, care, expiresAt = now + 60_000),
        ),
    )
    fs.assertFails(care, fs.list("invitations"))
    fs.assertSucceeds(care, fs.listInvitations(care, circleId))
    fs.assertFails(null, fs.listInvitations(care, circleId))
    fs.assertFails(
        care,
        fs.set(
            "invitations/WITHROLE",
            invite("WITHROLE", circleId, care, expiresAt = now + 60_000).plus("role" to "PATIENT"),
        ),
    )

    // Stranger is denied circle and caregiver profile reads.
    fs.assertFails(stranger, fs.get("careCircles/$circleId"))
    fs.assertFails(stranger, fs.get("users/$care"))
    fs.assertFails(
        stranger,
        fs.set(
            "invitations/HACK",
            invite("HACK", circleId, stranger, expiresAt = now + 60_000),
        ),
    )

    // Client cannot join a circle or grow circleIds (membership sync is backend-only).
    fs.assertSucceeds(
        peer,
        fs.set("users/$peer", user(peer, "CAREGIVER", emptyList(), "Bob")),
    )
    fs.assertFails(
        peer,
        fs.set(
            "careCircles/$circleId",
            circle(circleId, care, listOf(care, peer), patientId = ""),
        ),
    )
    fs.assertFails(peer, fs.patch("users/$peer", mapOf("circleIds" to listOf(circleId))))

    val peerCircleId = "c-peer"
    fs.assertSucceeds(
        peer,
        fs.commit(
            fs.setWrite("users/$peer", user(peer, "CAREGIVER", listOf(peerCircleId), "Bob")),
            fs.setWrite(
                "careCircles/$peerCircleId",
                circle(peerCircleId, peer, listOf(peer), patientId = ""),
            ),
        ),
    )
    fs.assertSucceeds(peer, fs.get("careCircles/$peerCircleId"))

    // Admin SDK seeds membership (stands in for Cloud Functions redeem in PR #83).
    fs.adminSet(
        "careCircles/$circleId",
        circle(circleId, care, listOf(care, peer), patientId = ""),
    )
    fs.adminSet("users/$peer", user(peer, "CAREGIVER", listOf(circleId), "Bob"))
    fs.assertFails(peer, fs.patch("users/$peer", mapOf("role" to "PATIENT")))
    fs.assertSucceeds(
        peer,
        fs.set(
            "invitations/PEER01",
            invite("PEER01", circleId, peer, expiresAt = now + 60_000),
        ),
    )
    fs.assertFails(care, fs.listInvitations(peer, circleId))
    fs.assertFails(peer, fs.listInvitations(care, circleId))
    fs.assertSucceeds(care, fs.get("users/$peer"))
    fs.assertFails(stranger, fs.get("users/$peer"))
  }

  @Test
  fun circleIdsUpdate_acceptsOnlyOneNewlyCreatedOwnCircle_enforcedByEmulator() {
    val care = "care"
    val first = "c1"
    val second = "c2"
    fs.adminSet("users/$care", user(care, "CAREGIVER", listOf(first), "Ada"))
    fs.adminSet("careCircles/$first", circle(first, care, listOf(care), patientId = ""))

    fs.assertSucceeds(
        care,
        fs.commit(
            fs.setWrite("users/$care", user(care, "CAREGIVER", listOf(first, second), "Ada")),
            fs.setWrite(
                "careCircles/$second",
                circle(second, care, listOf(care), patientId = ""),
            ),
        ),
    )

    fs.adminSet("users/$care", user(care, "CAREGIVER", listOf(first), "Ada"))
    fs.adminSet("careCircles/theirs", circle("theirs", "other", listOf("other"), patientId = ""))
    fs.assertFails(
        care,
        fs.set("users/$care", user(care, "CAREGIVER", listOf(first, "theirs"), "Ada")),
    )

    fs.assertFails(
        care,
        fs.commit(
            fs.setWrite("users/$care", user(care, "CAREGIVER", listOf(first, "c2b", "c3"), "Ada")),
            fs.setWrite("careCircles/c2b", circle("c2b", care, listOf(care), patientId = "")),
            fs.setWrite("careCircles/c3", circle("c3", care, listOf(care), patientId = "")),
        ),
    )

    fs.adminSet("users/$care", user(care, "CAREGIVER", listOf(first, second), "Ada"))
    fs.assertFails(care, fs.set("users/$care", user(care, "CAREGIVER", listOf(first), "Ada")))

    val pat = "pat"
    fs.adminSet("users/$pat", user(pat, "PATIENT", emptyList(), "Pat"))
    fs.assertFails(
        pat,
        fs.commit(
            fs.setWrite("users/$pat", user(pat, "PATIENT", listOf("c-pat"), "Pat")),
            fs.setWrite("careCircles/c-pat", circle("c-pat", pat, listOf(pat), patientId = "")),
        ),
    )
  }

  @Test
  fun universalInvitationPayload_isAcceptedAndStoredRoleFree_enforcedByEmulator() {
    val circleId = "c-universal"
    val care = "care"
    val code = "UNIVERSAL"
    fs.assertSucceeds(
        care,
        fs.commit(
            fs.setWrite("users/$care", user(care, "CAREGIVER", listOf(circleId), "Ada")),
            fs.setWrite(
                "careCircles/$circleId",
                circle(circleId, care, listOf(care), patientId = ""),
            ),
        ),
    )

    val payload =
        universalInvitationPayload(
            code = code,
            circleId = circleId,
            createdBy = care,
            createdAt = now,
            expiresAt = now + 60_000,
        )
    fs.assertSucceeds(care, fs.set("invitations/$code", payload))

    val (status, body) = fs.adminGet("invitations/$code")
    assertEquals(200, status)
    assertTrue(body.contains("\"$code\""))
    assertFalse(body.contains("\"role\""))
  }

  @Test
  fun invitationList_creatorQueryReturnsOnlyOwnCircleInvitations_enforcedByEmulator() {
    val sharedCircleId = "shared-circle"
    val outsideCircleId = "outside-circle"
    val creator = "creator"
    val otherCaregiver = "other-caregiver"

    fs.adminSet("users/$creator", user(creator, "CAREGIVER", listOf(sharedCircleId), "Ada"))
    fs.adminSet(
        "users/$otherCaregiver",
        user(otherCaregiver, "CAREGIVER", listOf(sharedCircleId), "Bea"),
    )
    fs.adminSet(
        "careCircles/$sharedCircleId",
        circle(sharedCircleId, creator, listOf(creator, otherCaregiver), patientId = ""),
    )
    fs.adminSet(
        "careCircles/$outsideCircleId",
        circle(outsideCircleId, otherCaregiver, listOf(otherCaregiver), patientId = ""),
    )
    fs.adminSet(
        "invitations/CREATOR_ACTIVE",
        invite("CREATOR_ACTIVE", sharedCircleId, creator, expiresAt = now + 60_000),
    )
    fs.adminSet(
        "invitations/CREATOR_USED",
        invite("CREATOR_USED", sharedCircleId, creator, expiresAt = now + 60_000, usedBy = "u"),
    )
    fs.adminSet(
        "invitations/OTHER",
        invite("OTHER", sharedCircleId, otherCaregiver, expiresAt = now + 60_000),
    )
    fs.adminSet(
        "invitations/OUTSIDE",
        invite("OUTSIDE", outsideCircleId, otherCaregiver, expiresAt = now + 60_000),
    )

    val (code, body) = fs.queryInvitations(creator, createdBy = creator, circleId = sharedCircleId)

    assertTrue("Expected creator query to succeed but HTTP $code", code in 200..299)
    assertEquals(setOf("CREATOR_ACTIVE", "CREATOR_USED"), invitationCodes(body))
  }

  @Test
  fun invitationList_deniesQueriesForOtherCreatorsOrForeignCircles_enforcedByEmulator() {
    val sharedCircleId = "shared-circle"
    val outsideCircleId = "outside-circle"
    val creator = "creator"
    val otherCaregiver = "other-caregiver"

    fs.adminSet("users/$creator", user(creator, "CAREGIVER", listOf(sharedCircleId), "Ada"))
    fs.adminSet(
        "users/$otherCaregiver",
        user(otherCaregiver, "CAREGIVER", listOf(sharedCircleId, outsideCircleId), "Bea"),
    )
    fs.adminSet(
        "careCircles/$sharedCircleId",
        circle(sharedCircleId, creator, listOf(creator, otherCaregiver), patientId = ""),
    )
    fs.adminSet(
        "careCircles/$outsideCircleId",
        circle(outsideCircleId, otherCaregiver, listOf(otherCaregiver), patientId = ""),
    )
    fs.adminSet(
        "invitations/CREATOR",
        invite("CREATOR", sharedCircleId, creator, expiresAt = now + 60_000),
    )
    fs.adminSet(
        "invitations/OUTSIDE",
        invite("OUTSIDE", outsideCircleId, creator, expiresAt = now + 60_000),
    )

    fs.assertFails(otherCaregiver, fs.listInvitations(creator, sharedCircleId))
    fs.assertFails(creator, fs.listInvitations(creator, outsideCircleId))
    fs.assertFails(creator, fs.list("invitations"))
  }

  @Test
  fun crossFamilyAccess_requiresMatchingProfileAndCircleFamilyIds_enforcedByEmulator() {
    val familyA = "family-a"
    val familyB = "family-b"
    val memberA = "member-a"
    val memberB = "member-b"

    fs.adminSet("users/$memberA", user(memberA, "CAREGIVER", listOf(familyA), "Ada"))
    fs.adminSet("users/$memberB", user(memberB, "CAREGIVER", listOf(familyB), "Bob"))
    fs.adminSet(
        "careCircles/$familyA",
        circle(familyA, memberA, listOf(memberA), patientId = ""),
    )
    // Simulates an inconsistent backend write: memberA is listed in family B, but their profile
    // identifies family A only. Client access must still be denied.
    fs.adminSet(
        "careCircles/$familyB",
        circle(familyB, memberB, listOf(memberA, memberB), patientId = ""),
    )

    fs.assertSucceeds(memberA, fs.get("careCircles/$familyA"))
    fs.assertFails(memberA, fs.get("careCircles/$familyB"))
    fs.assertFails(
        memberA,
        fs.set(
            "careCircles/$familyB/stories/cross-family",
            mapOf("id" to "cross-family", "title" to "Blocked"),
        ),
    )
    fs.assertFails(
        memberA,
        fs.set(
            "careCircles/$familyB/media/cross-family",
            media("cross-family", memberA, "careCircles/$familyB/media/cross-family.jpg"),
        ),
    )
  }

  @Test
  fun userCreate_rejectsInvalidRoleAndExtraFields_enforcedByEmulator() {
    val uid = "newbie"

    fs.assertFails(
        uid,
        fs.set("users/$uid", user(uid, "ADMIN", emptyList(), "Neo")),
    )
    fs.assertFails(
        uid,
        fs.set(
            "users/$uid",
            user(uid, "CAREGIVER", emptyList(), "Neo") + ("extra" to "nope"),
        ),
    )
    fs.assertSucceeds(
        uid,
        fs.set("users/$uid", user(uid, "CAREGIVER", emptyList(), "Neo")),
    )
  }

  @Test
  fun emptyCircle_roleAndNameChange_succeeds_enforcedByEmulator() {
    val uid = "existing-user"
    fs.assertSucceeds(uid, fs.set("users/$uid", user(uid, "PATIENT", emptyList(), "First")))

    fs.assertSucceeds(uid, fs.set("users/$uid", user(uid, "CAREGIVER", emptyList(), "Renamed")))
  }

  @Test
  fun emptyCircle_cannotAttachCircleIds_enforcedByEmulator() {
    val uid = "existing-user"
    fs.assertSucceeds(uid, fs.set("users/$uid", user(uid, "CAREGIVER", emptyList(), "First")))

    fs.assertFails(uid, fs.set("users/$uid", user(uid, "CAREGIVER", listOf("c1"), "First")))
  }

  @Test
  fun joinedProfile_roleChange_fails_enforcedByEmulator() {
    val uid = "joined-user"
    fs.adminSet("users/$uid", user(uid, "PATIENT", listOf("c-joined"), "Joined"))

    fs.assertFails(
        uid,
        fs.set("users/$uid", user(uid, "CAREGIVER", listOf("c-joined"), "Joined")),
    )
  }

  @Test
  fun inviteClientRedeemBatch_rejectedLeavesStateUnchanged_enforcedByEmulator() {
    val circleId = "c-batch"
    val care = "care"
    val pat = "pat"
    val inviteCode = "BATCH01"
    val inviteDoc = invite(inviteCode, circleId, care, expiresAt = now + 120_000)
    val circleBefore = circle(circleId, care, listOf(care), patientId = "")

    fs.assertSucceeds(
        care,
        fs.commit(
            fs.setWrite("users/$care", user(care, "CAREGIVER", listOf(circleId), "Ada")),
            fs.setWrite("careCircles/$circleId", circleBefore),
        ),
    )
    fs.assertSucceeds(care, fs.set("invitations/$inviteCode", inviteDoc))

    // Client batch redeem (invite + circle + profile) must fail atomically.
    fs.assertFails(
        pat,
        fs.commit(
            fs.setWrite("invitations/$inviteCode", inviteDoc + ("usedBy" to pat)),
            fs.setWrite(
                "careCircles/$circleId",
                circle(circleId, care, listOf(care, pat), patientId = pat),
            ),
            fs.setWrite("users/$pat", user(pat, "PATIENT", listOf(circleId), "Pat")),
        ),
    )

    // Invitation, circle, and profile must be unchanged after the rejected batch.
    val (inviteStatus, inviteBody) = fs.adminGet("invitations/$inviteCode")
    assertEquals(200, inviteStatus)
    assertTrue(inviteBody.contains("\"usedBy\""))
    assertTrue(inviteBody.contains("nullValue"))
    assertFalse(inviteBody.contains("\"$pat\""))

    val (circleStatus, circleBody) = fs.adminGet("careCircles/$circleId")
    assertEquals(200, circleStatus)
    assertTrue(circleBody.contains("\"$care\""))
    assertFalse(circleBody.contains("\"$pat\""))

    val (profileStatus, _) = fs.adminGet("users/$pat")
    assertEquals(404, profileStatus)
  }

  @Test
  fun inviteClientRedeemDenied_mediaStoriesAppointments_enforcedByEmulator() {
    val circleId = "c-redeem"
    val care = "care"
    val pat = "pat"
    fs.assertSucceeds(
        care,
        fs.commit(
            fs.setWrite("users/$care", user(care, "CAREGIVER", listOf(circleId), "Ada")),
            fs.setWrite(
                "careCircles/$circleId",
                circle(circleId, care, listOf(care), patientId = ""),
            ),
        ),
    )
    fs.assertSucceeds(
        care,
        fs.set(
            "invitations/REDEEM1",
            invite("REDEEM1", circleId, care, expiresAt = now + 120_000),
        ),
    )
    fs.adminSet(
        "invitations/EXPIRED",
        invite("EXPIRED", circleId, care, expiresAt = now - 1),
    )

    fs.assertSucceeds(pat, fs.get("invitations/REDEEM1"))
    fs.assertFails(pat, fs.patch("invitations/EXPIRED", mapOf("usedBy" to pat)))
    // Client redeem / join is denied; Admin SDK stands in for Cloud Functions (PR #83).
    fs.assertFails(pat, fs.patch("invitations/REDEEM1", mapOf("usedBy" to pat)))
    fs.assertFails(
        pat,
        fs.set(
            "careCircles/$circleId",
            circle(circleId, care, listOf(care, pat), patientId = pat),
        ),
    )
    fs.adminSet(
        "careCircles/$circleId",
        circle(circleId, care, listOf(care, pat), patientId = pat),
    )
    fs.adminSet("users/$pat", user(pat, "PATIENT", listOf(circleId), "Pat"))

    val mediaCol = "careCircles/$circleId/media"
    fs.assertSucceeds(
        pat,
        fs.set("$mediaCol/m1", media("m1", pat, "careCircles/$circleId/media/m1.jpg")),
    )
    fs.assertFails(
        pat,
        fs.set("$mediaCol/m2", media("m2", pat, "careCircles/other/media/m2.jpg")),
    )
    fs.assertFails(care, fs.patch("$mediaCol/m1", mapOf("description" to "nope")))
    fs.assertSucceeds(pat, fs.patch("$mediaCol/m1", mapOf("description" to "beach")))
    fs.assertSucceeds(
        pat,
        fs.set(
            "careCircles/$circleId/stories/s1",
            mapOf(
                "id" to "s1",
                "title" to "Trip",
                "description" to "",
                "createdBy" to pat,
                "mediaIds" to emptyList<String>(),
                "createdAt" to now,
            ),
        ),
    )
    // Every member writes appointments: each one may delete what the other created
    val appointmentPath = "careCircles/$circleId/appointments/a1"
    fs.assertSucceeds(care, fs.set(appointmentPath, appointment("a1", care)))
    fs.assertSucceeds(pat, fs.delete(appointmentPath))
    fs.assertSucceeds(pat, fs.set(appointmentPath, appointment("a1", pat)))
    fs.assertSucceeds(care, fs.delete(appointmentPath))
    fs.assertSucceeds(pat, fs.delete("$mediaCol/m1"))
  }

  /** The creations behind MediaRepositoryFirebase: a text has no file, and each has its id. */
  @Test
  fun mediaWrites_textMayHaveEmptyPath_idFieldRequired_enforcedByEmulator() {
    val circleId = "c-media"
    val pat = "pat"
    fs.adminSet("careCircles/$circleId", circle(circleId, pat, listOf(pat), patientId = pat))
    fs.adminSet("users/$pat", user(pat, "PATIENT", listOf(circleId), "Pat"))
    val mediaCol = "careCircles/$circleId/media"

    // a text has no file: an empty storagePath is accepted, on create and on update
    fs.assertSucceeds(pat, fs.set("$mediaCol/t1", text("t1", pat)))
    fs.assertSucceeds(pat, fs.patch("$mediaCol/t1", mapOf("text" to "Edited")))
    // a text can't point at a file, even one in the circle's folder
    fs.assertFails(
        pat,
        fs.set("$mediaCol/t2", text("t2", pat) + ("storagePath" to "$mediaCol/t2.jpg")),
    )
    // only a text may have no file (a picture in the circle's folder is accepted in
    // inviteClientRedeemDenied_mediaStoriesAppointments_enforcedByEmulator)
    fs.assertFails(pat, fs.set("$mediaCol/m1", media("m1", pat, storagePath = "")))
    // the id field must be there, and equal to the document's id
    fs.assertFails(pat, fs.set("$mediaCol/t3", text("t3", pat) - "id"))
    fs.assertFails(pat, fs.set("$mediaCol/t4", text("other", pat)))
  }

  /** The edits behind MediaRepository.updateMedia: only the editable fields may change. */
  @Test
  fun mediaUpdate_onlyEditableFieldsChange_enforcedByEmulator() {
    val circleId = "c-media-edit"
    val pat = "pat"
    fs.adminSet("careCircles/$circleId", circle(circleId, pat, listOf(pat), patientId = pat))
    fs.adminSet("users/$pat", user(pat, "PATIENT", listOf(circleId), "Pat"))
    val picture = "careCircles/$circleId/media/m1"
    fs.adminSet(picture, media("m1", pat, "careCircles/$circleId/media/m1.jpg"))

    fs.assertSucceeds(
        pat,
        fs.patch(picture, mapOf("description" to "Beach", "shownIds" to listOf(pat))),
    )
    // its author can't approve it, turn it into a text (leaving its file without a document),
    // date it differently, point it at another file, or give it away
    fs.assertFails(pat, fs.patch(picture, mapOf("status" to "PENDING")))
    fs.assertFails(pat, fs.patch(picture, mapOf("type" to "text", "storagePath" to "")))
    fs.assertFails(pat, fs.patch(picture, mapOf("createdAt" to 0L)))
    fs.assertFails(
        pat,
        fs.patch(picture, mapOf("storagePath" to "careCircles/$circleId/media/other.jpg")),
    )
    fs.assertFails(pat, fs.patch(picture, mapOf("authorId" to "someone")))
  }

  @Test
  fun aUserBeingDeleted_canReadButNoLongerWriteOrCreateAnything_enforcedByEmulator() {
    val circleId = "c-deleting"
    val gone = "gone"
    val other = "other"
    fs.adminSet(
        "careCircles/$circleId",
        circle(circleId, gone, listOf(gone, other), patientId = ""),
    )
    fs.adminSet(
        "users/$gone",
        user(gone, "CAREGIVER", listOf(circleId), "Ada") + ("deletionRequested" to true),
    )
    fs.adminSet("users/$other", user(other, "CAREGIVER", listOf(circleId), "Bob"))
    fs.adminSet("careCircles/$circleId/media/m1", text("m1", gone))

    fun newMediaFor(uid: String, id: String) =
        fs.set("careCircles/$circleId/media/$id", text(id, uid))

    fs.assertFails(
        gone,
        fs.set(
            "users/$gone",
            user(gone, "CAREGIVER", listOf(circleId), "Renamed") + ("deletionRequested" to true),
        ),
    )
    fs.assertFails(gone, newMediaFor(gone, "m2"))
    fs.assertFails(gone, fs.delete("careCircles/$circleId/media/m1"))
    fs.assertFails(
        gone,
        fs.set("careCircles/c-new", circle("c-new", gone, listOf(gone), patientId = "")),
    )
    fs.assertFails(
        gone,
        fs.set("invitations/code", invite("code", circleId, gone, expiresAt = FAR_FUTURE_EXPIRY)),
    )
    // a failed deletion must not lock the user out of their data, and the others aren't affected
    fs.assertSucceeds(gone, fs.get("users/$gone"))
    fs.assertSucceeds(gone, fs.get("careCircles/$circleId"))
    fs.assertSucceeds(gone, fs.get("careCircles/$circleId/media/m1"))
    fs.assertSucceeds(other, newMediaFor(other, "m3"))
    fs.assertSucceeds(
        other,
        fs.set("users/$other", user(other, "CAREGIVER", listOf(circleId), "Renamed")),
    )
  }

  /** The writes behind AppointmentRepository: the fields it sets, and only those. */
  @Test
  fun appointments_savedByTheWriter_withValidFields_enforcedByEmulator() {
    val circleId = "c-appointments"
    val care = "care"
    val care2 = "care2"
    val pat = "pat"
    fs.adminSet(
        "careCircles/$circleId",
        circle(circleId, care, listOf(care, care2, pat), patientId = pat),
    )
    fs.adminSet("users/$care", user(care, "CAREGIVER", listOf(circleId), "Ada"))
    fs.adminSet("users/$care2", user(care2, "CAREGIVER", listOf(circleId), "Bob"))
    fs.adminSet("users/$pat", user(pat, "PATIENT", listOf(circleId), "Pat"))
    val path = "careCircles/$circleId/appointments"
    val created = appointment("a1", care)

    fs.assertSucceeds(care, fs.set("$path/a1", created))
    // created as someone else, or with an end before its start
    fs.assertFails(care, fs.set("$path/a2", appointment("a2", care2)))
    fs.assertFails(care, fs.set("$path/a2", appointment("a2", care) + ("lastEndAt" to START - 1)))
    // a field Appointment doesn't have, an empty title, an attendee who isn't a member
    fs.assertFails(care, fs.set("$path/a2", appointment("a2", care) + ("color" to "red")))
    fs.assertFails(care, fs.set("$path/a2", appointment("a2", care) + ("title" to "")))
    fs.assertFails(
        care,
        fs.set("$path/a2", appointment("a2", care) + ("attendeeIds" to listOf("eve"))),
    )
    // the checks of validationError(): a blank title, no start, a duration out of 1..1440
    val other = appointment("a2", care)
    fs.assertFails(care, fs.set("$path/a2", other + ("title" to "  ")))
    fs.assertFails(care, fs.set("$path/a2", other + ("startAt" to 0L) + ("lastEndAt" to HOUR)))
    fs.assertFails(
        care,
        fs.set("$path/a2", other + ("durationMinutes" to 0) + ("lastEndAt" to START)),
    )
    // lastEndAt must be the end the app computes, 60 minutes after the start when no duration
    fs.assertFails(care, fs.set("$path/a2", other + ("lastEndAt" to START + 2 * HOUR)))
    fs.assertSucceeds(
        care,
        fs.set("$path/a3", appointment("a3", care) + ("durationMinutes" to null)),
    )
    // fields the app couldn't read back
    fs.assertFails(care, fs.set("$path/a2", other + ("notes" to 3L)))
    fs.assertFails(care, fs.set("$path/a2", other + ("location" to mapOf("city" to "Bern"))))
    fs.assertSucceeds(
        care,
        fs.set(
            "$path/a4",
            appointment("a4", care) + ("location" to mapOf("lat" to 46.5, "lng" to 6.6)),
        ),
    )
    // the patient may create one too
    fs.assertSucceeds(pat, fs.set("$path/a2", appointment("a2", pat)))

    // Any member may edit it, as the last editor: the fields the repository's update() writes
    val edit = mapOf("title" to "Dentist", "updatedBy" to care2, "updatedAt" to START)
    fs.assertFails(care2, fs.patch("$path/a1", edit + ("updatedBy" to care)))
    // but not who created it or when
    fs.assertFails(care2, fs.patch("$path/a1", edit + ("createdBy" to care2)))
    fs.assertFails(care2, fs.patch("$path/a1", edit + ("createdAt" to 1L)))
    // and an edit keeps a start and an end the calendar's query can use
    fs.assertFails(care2, fs.patch("$path/a1", edit + ("startAt" to "10:00")))
    fs.assertFails(care2, fs.patch("$path/a1", edit + ("lastEndAt" to START - 1)))
    // a new duration needs the new end too
    fs.assertFails(care2, fs.patch("$path/a1", edit + ("durationMinutes" to 30)))
    fs.assertSucceeds(
        care2,
        fs.patch("$path/a1", edit + ("durationMinutes" to 30) + ("lastEndAt" to START + HOUR / 2)),
    )
    fs.assertSucceeds(care2, fs.patch("$path/a1", edit))
    fs.assertSucceeds(pat, fs.patch("$path/a1", edit + ("updatedBy" to pat)))
  }

  /** The reads behind CareCircleRepository.getCircle(circleId). */
  @Test
  fun careCircleRead_allowsOnlySignedInMembersOfExistingCircle_enforcedByEmulator() {
    val circleId = "c-read"
    val care = "care"
    val pat = "pat"
    val stranger = "stranger"
    val circlePath = "careCircles/$circleId"
    fs.adminSet(circlePath, circle(circleId, care, listOf(care, pat), patientId = pat))
    fs.adminSet("users/$care", user(care, "CAREGIVER", listOf(circleId), "Ada"))
    fs.adminSet("users/$pat", user(pat, "PATIENT", listOf(circleId), "Pat"))
    fs.adminSet("users/$stranger", user(stranger, "CAREGIVER", emptyList(), "Eve"))

    fs.assertSucceeds(care, fs.get(circlePath))
    fs.assertSucceeds(pat, fs.get(circlePath))
    fs.assertFails(stranger, fs.get(circlePath))
    fs.assertFails(null, fs.get(circlePath))
    // A missing circle is denied rather than reported as not found, even to a signed-in user who
    // lists it in their profile, so the repository surfaces it as PermissionDenied.
    fs.adminSet("users/$care", user(care, "CAREGIVER", listOf(circleId, "c-missing"), "Ada"))
    fs.assertFails(care, fs.get("careCircles/c-missing"))
  }

  @Test
  fun personPhotos_oneOwnOrRecordedPersonEntry_enforcedByEmulator() {
    val circleId = "c-photos"
    val care = "care"
    val pat = "pat"
    val aunt = "aunt"
    val stranger = "stranger"
    val circlePath = "careCircles/$circleId"
    fun photo(personId: String, photoId: String = "portrait") =
        "careCircles/$circleId/people/$personId/$photoId.jpg"
    fun writePhotos(photos: Map<String, String>) =
        fs.patch(circlePath, mapOf("personPhotos" to photos))

    val people = mapOf("aunt" to mapOf("id" to aunt, "firstName" to "Aunt"))
    val seeded = circle(circleId, care, listOf(care, pat), patientId = pat) + ("people" to people)
    fs.adminSet(circlePath, seeded)
    fs.adminSet("users/$care", user(care, "CAREGIVER", listOf(circleId), "Ada"))
    fs.adminSet("users/$pat", user(pat, "PATIENT", listOf(circleId), "Pat"))
    fs.adminSet("users/$stranger", user(stranger, "CAREGIVER", emptyList(), "Eve"))

    val none = emptyMap<String, String>()
    val own = mapOf(pat to photo(pat))
    val ownLater = mapOf(pat to photo(pat, "later"))
    val ownAndAunt = mapOf(pat to photo(pat, "later"), aunt to photo(aunt))
    val ownAndAuntLater = mapOf(pat to photo(pat, "later"), aunt to photo(aunt, "later"))
    val auntOnly = mapOf(aunt to photo(aunt, "later"))
    // A missing personPhotos field is {}, so writing {} changes nothing the rules care about.
    fs.assertSucceeds(pat, writePhotos(none))
    // Another member's uid: pat is not the writer, and not a key of people.
    fs.assertFails(care, writePhotos(mapOf(pat to photo(pat))))
    // A key that is neither the writer nor recorded in people.
    fs.assertFails(care, writePhotos(mapOf("nobody" to photo("nobody"))))
    // Two keys in one write.
    fs.assertFails(care, writePhotos(mapOf(care to photo(care), aunt to photo(aunt))))
    // The folder must be the changed key, not someone else's.
    fs.assertFails(care, writePhotos(mapOf(care to photo(aunt))))
    // Four parts: rejected by parts.size() == 5, not by the folder name.
    fs.assertFails(care, writePhotos(mapOf(care to "careCircles/$circleId/media/portrait.jpg")))
    // Five parts whose folder is not people: parts[2] == 'people'.
    fs.assertFails(
        care,
        writePhotos(mapOf(care to "careCircles/$circleId/media/$care/portrait.jpg")),
    )
    // A path in another circle: parts[1] == circleId.
    fs.assertFails(
        care,
        writePhotos(mapOf(care to "careCircles/other/people/$care/portrait.jpg")),
    )
    // An extra segment.
    fs.assertFails(
        care,
        writePhotos(mapOf(care to "careCircles/$circleId/people/$care/extra/portrait.jpg")),
    )
    // The value has to be a string path.
    fs.assertFails(care, fs.patch(circlePath, mapOf("personPhotos" to mapOf(care to 1))))
    // Not a JPEG, and not a name containing '_'.
    fs.assertFails(
        care,
        writePhotos(mapOf(care to "careCircles/$circleId/people/$care/portrait.png")),
    )
    fs.assertFails(
        care,
        writePhotos(mapOf(care to "careCircles/$circleId/people/$care/my_photo.jpg")),
    )
    // Adding a person and their photo together: the key is not in people yet.
    fs.assertFails(
        care,
        fs.patch(
            circlePath,
            mapOf(
                "people" to (people + ("newborn" to mapOf("id" to "newborn", "firstName" to "Bo"))),
                "personPhotos" to mapOf("newborn" to photo("newborn")),
            ),
        ),
    )
    // A stranger is not a member.
    fs.assertFails(stranger, writePhotos(mapOf(stranger to photo(stranger))))

    // pat sets their own photo, replaces it, then adds, replaces, and removes aunt's.
    fs.assertSucceeds(pat, writePhotos(own))
    fs.assertSucceeds(pat, writePhotos(ownLater))
    fs.assertSucceeds(pat, writePhotos(ownAndAunt))
    fs.assertSucceeds(pat, writePhotos(ownAndAuntLater))
    fs.assertSucceeds(pat, writePhotos(auntOnly))
    // care may set their own photo beside aunt's.
    fs.assertSucceeds(care, writePhotos(mapOf(aunt to photo(aunt, "later"), care to photo(care))))
    fs.adminSet(
        circlePath,
        seeded + ("personPhotos" to mapOf(aunt to photo(care, "later"), care to photo(care))),
    )
    // A replacement that copies a path already stored for someone else.
    fs.assertFails(
        care,
        writePhotos(mapOf(aunt to photo(care, "later"), care to photo(care, "later"))),
    )
    // A person id that contains '_' cannot be a folder name.
    fs.adminSet(
        circlePath,
        seeded + ("people" to mapOf("au_nt" to mapOf("id" to "au_nt", "firstName" to "Ann"))),
    )
    fs.assertFails(care, writePhotos(mapOf("au_nt" to photo("au_nt"))))

    // Onboarding writes the CareCircle data class, so personPhotos is {} rather than absent.
    fs.assertSucceeds(
        care,
        fs.set(
            "careCircles/c-empty",
            circle("c-empty", care, listOf(care), patientId = "") +
                ("personPhotos" to emptyMap<String, String>()),
        ),
    )
    // A create that already points at a photo is refused.
    fs.assertFails(
        care,
        fs.set(
            "careCircles/c-born",
            circle("c-born", care, listOf(care), patientId = "") +
                ("personPhotos" to mapOf(care to "careCircles/c-born/people/$care/portrait.jpg")),
        ),
    )
    // A user being deleted may not change personPhotos.
    fs.adminSet(
        "users/$care",
        user(care, "CAREGIVER", listOf(circleId), "Ada") + ("deletionRequested" to true),
    )
    fs.adminSet(circlePath, seeded + ("personPhotos" to mapOf(care to photo(care))))
    fs.assertFails(care, writePhotos(mapOf(care to photo(care, "later"))))
    // pat may clear aunt's photo: aunt is a key of people. care's deletion flag does not apply.
    fs.adminSet(
        circlePath,
        seeded + ("personPhotos" to mapOf(aunt to photo(aunt), care to photo(care))),
    )
    fs.assertSucceeds(pat, writePhotos(mapOf(care to photo(care))))
    // pat may not clear care's photo: care is a member, not pat, and not in people.
    fs.adminSet(circlePath, seeded + ("personPhotos" to mapOf(care to photo(care))))
    fs.assertFails(pat, writePhotos(emptyMap()))
  }

  companion object {
    /** Year 2096: the invitation is still valid, so a refused write is due to something else. */
    private const val FAR_FUTURE_EXPIRY = 4_000_000_000_000L

    /** The start of the appointments of these tests, in epoch milliseconds. */
    private const val START = 1_800_000_000_000L

    /** One hour, in milliseconds. */
    private const val HOUR = 60 * 60_000L

    /** Wall-clock millis so invite expiry compares correctly to emulator `request.time`. */
    private val now: Long
      get() = System.currentTimeMillis()

    @BeforeClass
    @JvmStatic
    fun requireEmulator() {
      assumeTrue("Firestore emulator is not running on port 8080", emulatorReachable())
    }

    private fun emulatorReachable(): Boolean =
        try {
          val connection =
              (URL("http://127.0.0.1:8080/").openConnection() as HttpURLConnection).apply {
                connectTimeout = 500
                readTimeout = 500
                requestMethod = "GET"
              }
          connection.responseCode
          connection.disconnect()
          true
        } catch (_: Exception) {
          false
        }

    private fun user(uid: String, role: String, circleIds: List<String>, firstName: String) =
        mapOf(
            "uid" to uid,
            "role" to role,
            "person" to
                mapOf(
                    "id" to uid,
                    "firstName" to firstName,
                    "lastName" to "",
                    "nickname" to null,
                ),
            "circleIds" to circleIds,
        )

    private fun circle(
        id: String,
        createdBy: String,
        memberIds: List<String>,
        patientId: String,
    ) =
        mapOf(
            "id" to id,
            "name" to "Family",
            "patientId" to patientId,
            "memberIds" to memberIds,
            "people" to emptyMap<String, Any>(),
            "nicknames" to emptyMap<String, Any>(),
            "places" to emptyMap<String, Any>(),
            "createdBy" to createdBy,
            "createdAt" to 1L,
        )

    /** Extracts invitation codes from a `runQuery` response body. */
    private fun invitationCodes(runQueryBody: String): Set<String> =
        Regex(""""name"\s*:\s*"[^"]*/documents/invitations/([^"]+)"""")
            .findAll(runQueryBody)
            .map { it.groupValues[1] }
            .toSet()

    private fun invite(
        code: String,
        circleId: String,
        createdBy: String,
        expiresAt: Long,
        usedBy: String? = null,
    ) =
        universalInvitationPayload(
            code = code,
            circleId = circleId,
            createdBy = createdBy,
            createdAt = now,
            expiresAt = expiresAt,
        ) + ("usedBy" to usedBy)

    private fun text(id: String, authorId: String) =
        mapOf(
            "id" to id,
            "type" to "text",
            "authorId" to authorId,
            "storagePath" to "",
            "createdAt" to now,
            "description" to "",
            "shownIds" to emptyList<String>(),
            "authorizedIds" to emptyList<String>(),
            "status" to "APPROVED",
            "text" to "Hello",
        )

    /**
     * @return a valid appointment [id], created and last saved by [savedBy], with the fields
     *   AppointmentRepository writes.
     */
    private fun appointment(id: String, savedBy: String) =
        mapOf(
            "id" to id,
            "title" to "Doctor",
            "notes" to "",
            "startAt" to START,
            "durationMinutes" to 60,
            "location" to null,
            "attendeeIds" to listOf(savedBy),
            "lastEndAt" to START + 60 * 60_000L,
            "createdBy" to savedBy,
            "createdAt" to START - 1,
            "updatedBy" to savedBy,
            "updatedAt" to START - 1,
        )

    private fun media(id: String, authorId: String, storagePath: String) =
        mapOf(
            "id" to id,
            "type" to "picture",
            "authorId" to authorId,
            "storagePath" to storagePath,
            "createdAt" to now,
            "description" to "",
            "shownIds" to emptyList<String>(),
            "authorizedIds" to emptyList<String>(),
            "status" to "APPROVED",
            "width" to 1,
            "height" to 1,
        )
  }
}
