// Written by Alaabrine, with assistance from Cursor Agent.

package com.github.se.amber.storage

import com.github.se.amber.data.media.MAX_PHOTO_BYTES
import com.github.se.amber.emulator.FirestoreEmulatorClient
import com.github.se.amber.emulator.emulatorAcceptsHttp
import com.github.se.amber.emulator.firestoreEmulatorBaseUrl
import com.github.se.amber.emulator.storageEmulatorBaseUrl
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

  private val firestore = FirestoreEmulatorClient()
  private val storage = StorageEmulatorClient()

  @Before
  fun clearData() {
    firestore.clear()
  }

  @Test
  fun memberCanUploadAJpegUpTo2MiBToTheirOwnFolderOrAPersonInTheCircle() {
    adminSet("careCircles/$CIRCLE", circle())
    val own = photoPath("ada")
    val person = photoPath("tom")
    val exact = photoPath("ada", "exact.jpg")
    storage.deleteAsAdmin(own, person, exact)

    storage.assertSucceeds(objectPath = own)
    storage.assertSucceeds(objectPath = person)
    storage.assertSucceeds(objectPath = exact, bytes = ByteArray(MAX_PHOTO_BYTES.toInt()))
  }

  @Test
  fun refusesAnotherMembersFolderNonMembersNonJpegOversizeAndBadMetadata() {
    adminSet("careCircles/$CIRCLE", circle(people = listOf("tom", "to_m")))
    adminSet("careCircles/c_1", circle(id = "c_1"))
    val own = photoPath("ada")
    val bobs = photoPath("bob")
    val eves = photoPath("eve")
    val paths =
        listOf(
            own,
            bobs,
            eves,
            photoPath("ada", "photo.png"),
            photoPath("ada", "big.jpg"),
            photoPath("ada", "p_1.jpg"),
            photoPath("ada", ".jpg"),
            photoPath("to_m"),
            photoPath("ada", circleId = "c_1"),
            "careCircles/$CIRCLE/media/m1.jpg",
        )
    storage.deleteAsAdmin(*paths.toTypedArray())

    // bob is a member, but the folder is neither ada's uid nor a person in CareCircle.people
    storage.assertFails(objectPath = bobs)
    storage.assertFails("eve", eves)
    storage.assertFails(null, own)
    storage.assertFails(objectPath = photoPath("ada", "photo.png"), contentType = "image/png")
    storage.assertFails(
        objectPath = photoPath("ada", "big.jpg"),
        bytes = ByteArray((MAX_PHOTO_BYTES + 1).toInt()),
    )
    storage.assertFails(objectPath = photoPath("ada", "p_1.jpg"))
    storage.assertFails(objectPath = photoPath("ada", ".jpg"))
    storage.assertFails(objectPath = photoPath("to_m"))
    storage.assertFails(objectPath = photoPath("ada", circleId = "c_1"))
    storage.assertFails(objectPath = own, authorId = "bob")
    storage.assertFails(objectPath = own, extraMetadata = mapOf("extra" to "no"))

    storage.assertSucceeds(objectPath = own)
    storage.assertUpdateDenied(
        storage.patch(own, """{"contentType":"image/jpeg","metadata":{"authorId":"ada"}}"""),
    )

    // The media limit stays 25 MiB: a file just over the photo cap is still a valid media upload.
    storage.assertSucceeds(
        objectPath = "careCircles/$CIRCLE/media/m1.jpg",
        bytes = ByteArray((MAX_PHOTO_BYTES + 1).toInt()),
    )
  }

  @Test
  fun aMemberCanReadAPhotoAndANonMemberCannot() {
    adminSet("careCircles/$CIRCLE", circle())
    val own = photoPath("ada")
    storage.deleteAsAdmin(own)
    storage.assertSucceeds(objectPath = own)

    storage.assertReadSucceeds("ada", own)
    storage.assertReadSucceeds("bob", own)
    storage.assertReadFails("eve", own)
    storage.assertReadFails(null, own)
  }

  @Test
  fun theAuthorOrACaregiverCanDeleteAndAnotherMemberCannot() {
    adminSet("careCircles/$CIRCLE", circle(memberIds = listOf("ada", "bob", "cam")))
    adminSet("users/ada", mapOf<String, Any?>("role" to "CAREGIVER"))
    adminSet("users/bob", mapOf<String, Any?>("role" to "PATIENT"))
    adminSet("users/cam", mapOf<String, Any?>("role" to "PATIENT"))
    val first = photoPath("bob", "one.jpg")
    val second = photoPath("bob", "two.jpg")
    storage.deleteAsAdmin(first, second)

    storage.assertSucceeds("bob", first, authorId = "bob")
    storage.assertDeleteFails("cam", first)
    storage.assertDeleteSucceeds("ada", first)

    storage.assertSucceeds("bob", second, authorId = "bob")
    storage.assertDeleteSucceeds("bob", second)
  }

  @Test
  fun aUserBeingDeletedCannotUploadOrDeleteAPhoto() {
    adminSet("careCircles/$CIRCLE", circle())
    adminSet(
        "users/ada",
        mapOf<String, Any?>("role" to "CAREGIVER", "deletionRequested" to true),
    )
    val own = photoPath("ada")
    val bobs = photoPath("bob")
    storage.deleteAsAdmin(own, bobs)

    storage.assertFails(objectPath = own)
    storage.assertSucceeds("bob", bobs, authorId = "bob")
    storage.assertDeleteFails("ada", bobs)
  }

  private fun adminSet(path: String, fields: Map<String, Any?>) {
    val code = firestore.adminSet(path, fields)
    assertTrue("Admin set of $path failed with HTTP $code", code in 200..299)
  }

  companion object {
    private const val CIRCLE = "c1"
    private const val BUCKET = "family-bucket"

    @BeforeClass
    @JvmStatic
    fun requireEmulators() {
      assumeTrue(
          "Firestore and Storage emulators are not both running",
          emulatorAcceptsHttp(firestoreEmulatorBaseUrl()) &&
              emulatorAcceptsHttp(storageEmulatorBaseUrl()),
      )
    }

    private fun photoPath(personId: String, name: String = "photo.jpg", circleId: String = CIRCLE) =
        "careCircles/$circleId/people/$personId/$name"

    private fun circle(
        people: List<String> = listOf("tom"),
        memberIds: List<String> = listOf("ada", "bob"),
        id: String = CIRCLE,
    ): Map<String, Any?> =
        mapOf(
            "id" to id,
            "name" to "Family",
            "patientId" to "",
            "memberIds" to memberIds,
            "people" to
                people.associateWith { personId ->
                  mapOf("id" to personId, "firstName" to personId)
                },
            "createdBy" to "ada",
            "createdAt" to 1L,
            "storageBucket" to BUCKET,
        )
  }
}

/**
 * Uploads to the Storage emulator the way the Firebase SDK does: one multipart body carrying the
 * content type and the `authorId` custom metadata the rules require.
 */
private class StorageEmulatorClient(
    private val projectId: String = "amber-34abf",
    private val host: String = storageEmulatorBaseUrl(),
) {
  fun deleteAsAdmin(vararg objectPaths: String) {
    for (path in objectPaths) {
      val code = request("DELETE", objectUrl(path), token = "owner").first
      assertTrue("Admin delete of $path failed with HTTP $code", code == 204 || code == 404)
    }
  }

  fun assertSucceeds(
      uid: String? = "ada",
      objectPath: String,
      contentType: String = "image/jpeg",
      bytes: ByteArray = JPEG,
      authorId: String = "ada",
      extraMetadata: Map<String, String> = emptyMap(),
  ) {
    val (code, body) = upload(uid, objectPath, contentType, bytes, authorId, extraMetadata)
    assertTrue("Expected upload of $objectPath to succeed, HTTP $code: $body", code in 200..299)
  }

  fun assertFails(
      uid: String? = "ada",
      objectPath: String,
      contentType: String = "image/jpeg",
      bytes: ByteArray = JPEG,
      authorId: String = "ada",
      extraMetadata: Map<String, String> = emptyMap(),
  ) {
    val (code, body) = upload(uid, objectPath, contentType, bytes, authorId, extraMetadata)
    assertEquals("Expected upload of $objectPath to be denied: $body", 403, code)
  }

  fun assertReadSucceeds(uid: String?, objectPath: String) {
    val (code, body) = request("GET", objectUrl(objectPath), token = bearer(uid))
    assertTrue("Expected read of $objectPath to succeed, HTTP $code: $body", code in 200..299)
  }

  fun assertReadFails(uid: String?, objectPath: String) {
    val (code, body) = request("GET", objectUrl(objectPath), token = bearer(uid))
    assertEquals("Expected read of $objectPath to be denied: $body", 403, code)
  }

  fun assertDeleteSucceeds(uid: String?, objectPath: String) {
    val (code, body) = request("DELETE", objectUrl(objectPath), token = bearer(uid))
    assertTrue("Expected delete of $objectPath to succeed, HTTP $code: $body", code in 200..299)
  }

  fun assertDeleteFails(uid: String?, objectPath: String) {
    val (code, body) = request("DELETE", objectUrl(objectPath), token = bearer(uid))
    assertEquals("Expected delete of $objectPath to be denied: $body", 403, code)
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
      extraMetadata: Map<String, String>,
  ): Pair<Int, String> {
    val boundary = "amberboundary"
    val metadata =
        (mapOf("authorId" to authorId) + extraMetadata).entries.joinToString(",") { (key, value) ->
          "\"$key\":\"$value\""
        }
    val head =
        ("--$boundary\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n" +
                "\r\n" +
                """{"contentType":"$contentType","metadata":{$metadata}}""" +
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
                """{"iss":"https://securetoken.google.com/$projectId","aud":"$projectId","iat":1700000000,"exp":1800000000,"sub":"$uid","user_id":"$uid","firebase":{"sign_in_provider":"custom"}}""".toByteArray(),
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

  private companion object {
    val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
  }
}
