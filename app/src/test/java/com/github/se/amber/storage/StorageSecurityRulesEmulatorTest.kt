// Written by Alaabrine, with assistance from Cursor Agent.

package com.github.se.amber.storage

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

/**
 * Emulator checks against the real `functions/storage.rules`.
 *
 * Person-photo writes go to the Storage emulator; the circle document they read lives in the
 * Firestore emulator, which the Storage emulator calls through `firestore.get`. Requires both, from
 * `firebase.json`. Example: `firebase emulators:exec --project amber-34abf --only firestore,storage
 * "./gradlew :app:testDebugUnitTest --tests
 * com.github.se.amber.storage.StorageSecurityRulesEmulatorTest"`
 *
 * A second upload of the same object is evaluated as a create by the Storage emulator, so "no
 * updates" is checked with a metadata update, which the rules also refuse.
 */
class StorageSecurityRulesEmulatorTest {

  private val firestore = FirestoreAdminClient()
  private val storage = StorageEmulatorClient()

  @Before
  fun clearData() {
    assumeTrue("Firestore and Storage emulators are not both running", emulatorsReachable())
    firestore.clear()
  }

  @Test
  fun memberCanUploadAJpegUpTo2MiBToTheirOwnFolderOrAPersonInTheCircle() {
    firestore.adminSet("careCircles/$CIRCLE", circle(people = listOf("tom")))
    val own = "careCircles/$CIRCLE/people/ada/photo.jpg"
    val person = "careCircles/$CIRCLE/people/tom/photo.jpg"
    val exact = "careCircles/$CIRCLE/people/ada/exact.jpg"
    storage.deleteAsAdmin(own, person, exact)

    storage.assertSucceeds("ada", own, "image/jpeg", JPEG, authorId = "ada")
    storage.assertSucceeds("ada", person, "image/jpeg", JPEG, authorId = "ada")
    storage.assertSucceeds("ada", exact, "image/jpeg", ByteArray(MAX_PHOTO_BYTES), authorId = "ada")
  }

  @Test
  fun refusesAnotherMembersFolderNonMembersNonJpegOversizeAndUpdates() {
    firestore.adminSet("careCircles/$CIRCLE", circle(people = listOf("tom")))
    val own = "careCircles/$CIRCLE/people/ada/photo.jpg"
    val bobs = "careCircles/$CIRCLE/people/bob/photo.jpg"
    val eves = "careCircles/$CIRCLE/people/eve/photo.jpg"
    val png = "careCircles/$CIRCLE/people/ada/photo.png"
    val tooBig = "careCircles/$CIRCLE/people/ada/big.jpg"
    val media = "careCircles/$CIRCLE/media/m1.jpg"
    storage.deleteAsAdmin(own, bobs, eves, png, tooBig, media)

    // bob is a member, but the folder is neither ada's uid nor a person in CareCircle.people
    storage.assertFails("ada", bobs, "image/jpeg", JPEG, authorId = "ada")
    storage.assertFails("eve", eves, "image/jpeg", JPEG, authorId = "eve")
    storage.assertFails(null, own, "image/jpeg", JPEG, authorId = "ada")
    storage.assertFails("ada", png, "image/png", JPEG, authorId = "ada")
    storage.assertFails(
        "ada",
        tooBig,
        "image/jpeg",
        ByteArray(MAX_PHOTO_BYTES + 1),
        authorId = "ada",
    )

    storage.assertSucceeds("ada", own, "image/jpeg", JPEG, authorId = "ada")
    storage.assertUpdateDenied(
        storage.patch(own, """{"contentType":"image/jpeg","metadata":{"authorId":"ada"}}"""),
    )

    // The media limit stays 25 MiB: a file just over the photo cap is still a valid media upload.
    storage.assertSucceeds(
        "ada",
        media,
        "image/jpeg",
        ByteArray(MAX_PHOTO_BYTES + 1),
        authorId = "ada",
    )
  }

  companion object {
    private const val CIRCLE = "c1"
    private const val BUCKET = "family-bucket"
    private const val MAX_PHOTO_BYTES = 2 * 1024 * 1024
    private val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

    @BeforeClass
    @JvmStatic
    fun requireEmulators() {
      assumeTrue("Firestore and Storage emulators are not both running", emulatorsReachable())
    }

    private fun emulatorsReachable(): Boolean =
        portAcceptsHttp("http://127.0.0.1:8080/") && portAcceptsHttp("http://127.0.0.1:9199/")

    private fun portAcceptsHttp(url: String): Boolean =
        try {
          val connection =
              (URL(url).openConnection() as HttpURLConnection).apply {
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

    private fun circle(people: List<String>): Map<String, Any?> =
        mapOf(
            "id" to CIRCLE,
            "name" to "Family",
            "patientId" to "",
            "memberIds" to listOf("ada", "bob"),
            "people" to people.associateWith { id -> mapOf("id" to id, "firstName" to id) },
            "createdBy" to "ada",
            "createdAt" to 1L,
            "storageBucket" to BUCKET,
        )
  }
}

/** Writes care-circle documents with the emulator's admin bypass, the way the rules read them. */
private class FirestoreAdminClient(
    private val projectId: String = "amber-34abf",
    private val host: String =
        System.getenv("FIRESTORE_EMULATOR_HOST")?.let { "http://$it" } ?: "http://127.0.0.1:8080",
) {
  fun clear() {
    val code =
        request(
            "DELETE",
            "$host/emulator/v1/projects/$projectId/databases/(default)/documents",
        )
    assertTrue("Emulator clear failed with HTTP $code", code in 200..299)
  }

  fun adminSet(path: String, fields: Map<String, Any?>) {
    val code =
        request(
            "POST",
            "$host/v1/projects/$projectId/databases/(default)/documents:commit",
            token = "owner",
            json =
                """{"writes":[{"update":{"name":"projects/$projectId/databases/(default)/documents/$path","fields":${encodeFields(fields)}}}]}""",
        )
    assertTrue("Admin set of $path failed with HTTP $code", code in 200..299)
  }

  private fun request(
      method: String,
      url: String,
      token: String? = null,
      json: String? = null,
  ): Int {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.requestMethod = method
    connection.connectTimeout = 5_000
    connection.readTimeout = 5_000
    if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
    if (json != null) {
      connection.doOutput = true
      connection.setRequestProperty("Content-Type", "application/json")
      connection.outputStream.use { it.write(json.toByteArray()) }
    }
    return try {
      connection.responseCode
    } finally {
      connection.disconnect()
    }
  }
}

/**
 * Uploads to the Storage emulator the way the Firebase SDK does: one multipart body carrying the
 * content type and the `authorId` custom metadata the rules require.
 */
private class StorageEmulatorClient(
    private val projectId: String = "amber-34abf",
    private val host: String =
        System.getenv("FIREBASE_STORAGE_EMULATOR_HOST")?.let { "http://$it" }
            ?: "http://127.0.0.1:9199",
) {
  fun deleteAsAdmin(vararg objectPaths: String) {
    for (path in objectPaths) {
      val code = request("DELETE", objectUrl(path), token = "owner").first
      assertTrue("Admin delete of $path failed with HTTP $code", code == 204 || code == 404)
    }
  }

  fun assertSucceeds(
      uid: String?,
      objectPath: String,
      contentType: String,
      bytes: ByteArray,
      authorId: String,
  ) {
    val (code, body) = upload(uid, objectPath, contentType, bytes, authorId)
    assertTrue("Expected upload of $objectPath to succeed, HTTP $code: $body", code in 200..299)
  }

  fun assertFails(
      uid: String?,
      objectPath: String,
      contentType: String,
      bytes: ByteArray,
      authorId: String,
  ) {
    val (code, body) = upload(uid, objectPath, contentType, bytes, authorId)
    assertEquals("Expected upload of $objectPath to be denied: $body", 403, code)
  }

  fun assertUpdateDenied(result: Pair<Int, String>) {
    assertEquals("Expected the update to be denied: ${result.second}", 403, result.first)
  }

  fun patch(objectPath: String, metadataJson: String): Pair<Int, String> =
      // HttpURLConnection rejects PATCH. The emulator treats this override as a metadata update.
      request(
          "PUT",
          objectUrl(objectPath),
          token = bearer("ada"),
          contentType = "application/json",
          extraHeaders = mapOf("X-Http-Method-Override" to "PATCH"),
          body = metadataJson.toByteArray(),
      )

  private fun upload(
      uid: String?,
      objectPath: String,
      contentType: String,
      bytes: ByteArray,
      authorId: String,
  ): Pair<Int, String> {
    val boundary = "amberboundary"
    val metadata = """{"contentType":"$contentType","metadata":{"authorId":"$authorId"}}"""
    val head =
        ("--$boundary\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n" +
                "\r\n" +
                metadata +
                "\r\n" +
                "--$boundary\r\n" +
                "Content-Type: $contentType\r\n" +
                "\r\n")
            .toByteArray()
    val tail = "\r\n--$boundary--\r\n".toByteArray()
    val body = ByteArray(head.size + bytes.size + tail.size)
    head.copyInto(body)
    bytes.copyInto(body, head.size)
    tail.copyInto(body, head.size + bytes.size)
    val name = URLEncoder.encode(objectPath, Charsets.UTF_8)
    return request(
        "POST",
        "$host/v0/b/family-bucket/o?name=$name",
        token = bearer(uid),
        contentType = "multipart/related; boundary=$boundary",
        extraHeaders = mapOf("x-goog-upload-protocol" to "multipart"),
        body = body,
    )
  }

  private fun objectUrl(objectPath: String): String {
    val name = URLEncoder.encode(objectPath, Charsets.UTF_8)
    return "$host/v0/b/family-bucket/o/$name"
  }

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
      contentType: String? = null,
      extraHeaders: Map<String, String> = emptyMap(),
      body: ByteArray? = null,
  ): Pair<Int, String> {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.requestMethod = method
    connection.connectTimeout = 10_000
    connection.readTimeout = 20_000
    if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
    if (contentType != null) connection.setRequestProperty("Content-Type", contentType)
    extraHeaders.forEach { (name, value) -> connection.setRequestProperty(name, value) }
    if (body != null) {
      connection.doOutput = true
      connection.outputStream.use { it.write(body) }
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
}

@Suppress("UNCHECKED_CAST")
private fun encodeFields(fields: Map<String, Any?>): String =
    fields.entries.joinToString(",", "{", "}") { (key, value) -> "\"$key\":${encodeValue(value)}" }

@Suppress("UNCHECKED_CAST")
private fun encodeValue(value: Any?): String =
    when (value) {
      null -> """{"nullValue":null}"""
      is String -> """{"stringValue":"$value"}"""
      is Boolean -> """{"booleanValue":$value}"""
      is Int -> """{"integerValue":"$value"}"""
      is Long -> """{"integerValue":"$value"}"""
      is List<*> -> """{"arrayValue":{"values":[${value.joinToString(",") { encodeValue(it) }}]}}"""
      is Map<*, *> -> """{"mapValue":{"fields":${encodeFields(value as Map<String, Any?>)}}}"""
      else -> error("Unsupported Firestore value: ${value::class}")
    }
