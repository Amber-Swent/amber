// Written by Alaabrine, with assistance from
// Cursor (Composer).
package com.github.se.amber.firestore

import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import org.junit.Assert.assertEquals
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
            invite("JOIN01", circleId, "PATIENT", care, expiresAt = now + 60_000),
        ),
    )
    fs.assertFails(care, fs.list("invitations"))

    // Stranger is denied circle and caregiver profile reads.
    fs.assertFails(stranger, fs.get("careCircles/$circleId"))
    fs.assertFails(stranger, fs.get("users/$care"))
    fs.assertFails(
        stranger,
        fs.set(
            "invitations/HACK",
            invite("HACK", circleId, "CAREGIVER", stranger, expiresAt = now + 60_000),
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

    // Admin SDK seeds membership (stands in for Cloud Functions redeem in PR #83).
    fs.adminSet(
        "careCircles/$circleId",
        circle(circleId, care, listOf(care, peer), patientId = ""),
    )
    fs.adminSet("users/$peer", user(peer, "CAREGIVER", listOf(circleId), "Bob"))
    fs.assertSucceeds(care, fs.get("users/$peer"))
    fs.assertFails(stranger, fs.get("users/$peer"))
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
            invite("REDEEM1", circleId, "PATIENT", care, expiresAt = now + 120_000),
        ),
    )
    fs.adminSet(
        "invitations/EXPIRED",
        invite("EXPIRED", circleId, "CAREGIVER", care, expiresAt = now - 1),
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

    private fun invite(
        code: String,
        circleId: String,
        role: String,
        createdBy: String,
        expiresAt: Long,
        usedBy: String? = null,
    ) =
        mapOf(
            "code" to code,
            "circleId" to circleId,
            "role" to role,
            "createdBy" to createdBy,
            "createdAt" to now,
            "expiresAt" to expiresAt,
            "usedBy" to usedBy,
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
    private val host: String = "http://127.0.0.1:8080",
) {
  fun clear() {
    request(
        "DELETE",
        "$host/emulator/v1/projects/$projectId/databases/(default)/documents",
        token = null,
    )
  }

  fun adminSet(path: String, fields: Map<String, Any?>): Int =
      commitAs("owner", setWrite(path, fields))

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
  ): Int {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.requestMethod = method
    connection.connectTimeout = 5_000
    connection.readTimeout = 5_000
    if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
    if (body != null) {
      connection.doOutput = true
      connection.setRequestProperty("Content-Type", "application/json")
      connection.outputStream.use { it.write(jsonEncode(body).toByteArray()) }
    }
    return try {
      connection.responseCode
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
}
