// Written by Alaabrine, with assistance from
// Cursor (Composer).
// Modified by Neha Chakraborty and Copilot.
// Modified by Neha Chakraborty and Claude Code.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.firestore

import com.github.se.amber.data.invitation.universalInvitationPayload
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
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
  fun profileOverwrite_cannotChangeRoleOrCircles_enforcedByEmulator() {
    val uid = "existing-user"
    fs.assertSucceeds(uid, fs.set("users/$uid", user(uid, "PATIENT", emptyList(), "First")))

    fs.assertFails(uid, fs.set("users/$uid", user(uid, "CAREGIVER", emptyList(), "First")))
    fs.assertFails(uid, fs.set("users/$uid", user(uid, "PATIENT", listOf("c1"), "First")))
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
    val appointment =
        mapOf(
            "id" to "a1",
            "title" to "Doctor",
            "notes" to "",
            "startAt" to now,
            "attendeeIds" to emptyList<String>(),
            "createdBy" to care,
            "createdAt" to now,
        )
    fs.assertFails(pat, fs.set("careCircles/$circleId/appointments/a1", appointment))
    fs.assertSucceeds(care, fs.set("careCircles/$circleId/appointments/a1", appointment))
    fs.assertFails(pat, fs.delete("careCircles/$circleId/appointments/a1"))
    fs.assertSucceeds(care, fs.delete("careCircles/$circleId/appointments/a1"))
    fs.assertSucceeds(pat, fs.delete("$mediaCol/m1"))
  }

  /** The writes behind MediaRepositoryFirebase: a text has no file, and every write has its id. */
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
    // a text can't point at a file outside the circle
    fs.assertFails(
        pat,
        fs.set("$mediaCol/t2", text("t2", pat) + ("storagePath" to "careCircles/other/media/t2")),
    )
    // only a text may have no file (a picture in the circle's folder is accepted in
    // inviteClientRedeemDenied_mediaStoriesAppointments_enforcedByEmulator)
    fs.assertFails(pat, fs.set("$mediaCol/m1", media("m1", pat, storagePath = "")))
    // the id field must be there, and equal to the document's id
    fs.assertFails(pat, fs.set("$mediaCol/t3", text("t3", pat) - "id"))
    fs.assertFails(pat, fs.set("$mediaCol/t4", text("other", pat)))
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

  companion object {
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

/** Minimal Firestore emulator REST client with per-request auth uid. */
private class FirestoreEmulatorClient(
    private val projectId: String = "amber-34abf",
    private val host: String =
        System.getenv("FIRESTORE_EMULATOR_HOST")?.let { "http://$it" } ?: "http://127.0.0.1:8080",
) {
  fun clear() {
    val code =
        request(
            "DELETE",
            "$host/emulator/v1/projects/$projectId/databases/(default)/documents",
            token = null,
        )
    assertTrue("Emulator clear failed with HTTP $code", code in 200..299)
  }

  fun adminSet(path: String, fields: Map<String, Any?>): Int =
      commitAs("owner", setWrite(path, fields))

  /** Admin read that returns HTTP status and raw JSON body (bypasses security rules). */
  fun adminGet(path: String): Pair<Int, String> =
      requestWithBody("GET", docUrl(path), token = "owner")

  fun set(path: String, fields: Map<String, Any?>): (String?) -> Int = { uid ->
    commitAs(bearer(uid), setWrite(path, fields))
  }

  fun patch(path: String, fields: Map<String, Any?>): (String?) -> Int = { uid ->
    commitAs(
        bearer(uid),
        mapOf(
            "update" to mapOf("name" to docName(path), "fields" to encodeFields(fields)),
            "updateMask" to mapOf("fieldPaths" to fields.keys.toList()),
        ),
    )
  }

  fun get(path: String): (String?) -> Int = { uid ->
    request("GET", docUrl(path), token = bearer(uid))
  }

  fun list(collectionId: String): (String?) -> Int = { uid ->
    request("GET", docUrl(collectionId), token = bearer(uid))
  }

  fun listInvitations(createdBy: String, circleId: String): (String?) -> Int = { uid ->
    queryInvitations(uid, createdBy, circleId).first
  }

  /** Runs the repository's `createdBy` + `circleId` query and returns HTTP status and JSON body. */
  fun queryInvitations(uid: String?, createdBy: String, circleId: String): Pair<Int, String> =
      requestWithBody(
          "POST",
          "$host/v1/projects/$projectId/databases/(default)/documents:runQuery",
          token = bearer(uid),
          body =
              mapOf(
                  "structuredQuery" to
                      mapOf(
                          "from" to listOf(mapOf("collectionId" to "invitations")),
                          "where" to
                              mapOf(
                                  "compositeFilter" to
                                      mapOf(
                                          "op" to "AND",
                                          "filters" to
                                              listOf(
                                                  mapOf(
                                                      "fieldFilter" to
                                                          mapOf(
                                                              "field" to
                                                                  mapOf("fieldPath" to "createdBy"),
                                                              "op" to "EQUAL",
                                                              "value" to
                                                                  mapOf("stringValue" to createdBy),
                                                          ),
                                                  ),
                                                  mapOf(
                                                      "fieldFilter" to
                                                          mapOf(
                                                              "field" to
                                                                  mapOf("fieldPath" to "circleId"),
                                                              "op" to "EQUAL",
                                                              "value" to
                                                                  mapOf("stringValue" to circleId),
                                                          ),
                                                  ),
                                              ),
                                      ),
                              ),
                      ),
              ),
      )

  fun delete(path: String): (String?) -> Int = { uid ->
    commitAs(bearer(uid), mapOf("delete" to docName(path)))
  }

  fun setWrite(path: String, fields: Map<String, Any?>): Map<String, Any?> =
      mapOf("update" to mapOf("name" to docName(path), "fields" to encodeFields(fields)))

  fun commit(vararg writes: Map<String, Any?>): (String?) -> Int = { uid ->
    commitAs(bearer(uid), *writes)
  }

  private fun commitAs(token: String?, vararg writes: Map<String, Any?>): Int =
      request(
          "POST",
          "$host/v1/projects/$projectId/databases/(default)/documents:commit",
          token = token,
          body = mapOf("writes" to writes.toList()),
      )

  fun assertSucceeds(uid: String?, op: (String?) -> Int) {
    val code = op(uid)
    assertTrue("Expected success for uid=$uid but HTTP $code", code in 200..299)
  }

  fun assertSucceeds(op: () -> Int) {
    val code = op()
    assertTrue("Expected success but HTTP $code", code in 200..299)
  }

  fun assertFails(uid: String?, op: (String?) -> Int) {
    assertEquals("Expected PERMISSION_DENIED for uid=$uid", 403, op(uid))
  }

  private fun docUrl(path: String) =
      "$host/v1/projects/$projectId/databases/(default)/documents/$path"

  private fun docName(path: String) = "projects/$projectId/databases/(default)/documents/$path"

  private fun bearer(uid: String?): String? {
    if (uid == null) return null
    val header =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("""{"alg":"none","typ":"JWT"}""".toByteArray())
    val payload =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                """{"iss":"https://securetoken.google.com/$projectId","aud":"$projectId","iat":1700000000,"exp":1800000000,"sub":"$uid","user_id":"$uid","firebase":{"sign_in_provider":"custom"}}"""
                    .toByteArray(),
            )
    return "$header.$payload."
  }

  private fun request(
      method: String,
      url: String,
      token: String?,
      body: Map<String, Any?>? = null,
  ): Int = requestWithBody(method, url, token, body).first

  private fun requestWithBody(
      method: String,
      url: String,
      token: String?,
      body: Map<String, Any?>? = null,
  ): Pair<Int, String> {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.requestMethod = method
    connection.connectTimeout = REQUEST_TIMEOUT_MS
    connection.readTimeout = REQUEST_TIMEOUT_MS
    if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
    if (body != null) {
      connection.doOutput = true
      connection.setRequestProperty("Content-Type", "application/json")
      connection.outputStream.use { it.write(jsonEncode(body).toByteArray()) }
    }
    return try {
      val code = connection.responseCode
      val stream = if (code in 200..299) connection.inputStream else connection.errorStream
      val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
      code to text
    } finally {
      connection.disconnect()
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun encodeFields(fields: Map<String, Any?>): Map<String, Any?> =
      fields.mapValues { (_, value) ->
        encodeValue(value)
      }

  @Suppress("UNCHECKED_CAST")
  private fun encodeValue(value: Any?): Map<String, Any?> =
      when (value) {
        null -> mapOf("nullValue" to null)
        is String -> mapOf("stringValue" to value)
        is Boolean -> mapOf("booleanValue" to value)
        is Int -> mapOf("integerValue" to value.toString())
        is Long -> mapOf("integerValue" to value.toString())
        is Double -> mapOf("doubleValue" to value)
        is List<*> -> mapOf("arrayValue" to mapOf("values" to value.map { encodeValue(it) }))
        is Map<*, *> ->
            mapOf(
                "mapValue" to mapOf("fields" to encodeFields(value as Map<String, Any?>)),
            )
        else -> error("Unsupported Firestore value: ${value::class} = $value")
      }

  private fun jsonEncode(value: Any?): String =
      when (value) {
        null -> "null"
        is String -> "\"${value.replace("\"", "\\\"")}\""
        is Number,
        is Boolean -> value.toString()
        is Map<*, *> ->
            value.entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\":${jsonEncode(v)}" }
        is List<*> -> value.joinToString(",", "[", "]") { jsonEncode(it) }
        else -> error("Cannot JSON-encode ${value::class}")
      }

  private companion object {
    const val REQUEST_TIMEOUT_MS = 5_000
  }
}
