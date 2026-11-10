// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.e2e

import com.google.firebase.Firebase
import com.google.firebase.app
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * Admin access to the Firebase emulators, for what only the backend does, and for removing what an
 * end-to-end test created. Everything a user's phone does goes through the app's own repositories
 * instead: this object only stands in for the backend.
 *
 * The security rules decide what a signed-in user's phone may read and write. Some data only the
 * backend (Cloud Functions, with admin rights) may write, and the rules refuse it from any phone:
 * - [setStorageBucket] records a circle's storage bucket. In production, the backend function
 *   `provisionCareCircleBucket` (`functions/index.js`) creates a bucket for each new circle and
 *   records it in the circle's `storageBucket` field. The emulators can't create buckets, so that
 *   function skips itself there, and a test records one instead.
 * - The cleanup functions delete what no phone may delete (a circle, another user's account). They
 *   delete only the ids given to them, never all of an emulator's data, so tests sharing the
 *   emulators don't delete each other's data.
 *
 * The emulators treat a request carrying `Authorization: Bearer owner` as an admin one: the rules
 * don't apply to it. Every URL points at [FirebaseEmulator.HOST], so this can never reach the real
 * project.
 */
object EmulatorAdmin {
  private const val DATABASE = "projects/${FirebaseEmulator.PROJECT_ID}/databases/(default)"
  private const val FIRESTORE =
      "http://${FirebaseEmulator.HOST}:${FirebaseEmulator.FIRESTORE_PORT}/v1/$DATABASE"
  private const val AUTH =
      "http://${FirebaseEmulator.HOST}:${FirebaseEmulator.AUTH_PORT}" +
          "/identitytoolkit.googleapis.com/v1/projects/${FirebaseEmulator.PROJECT_ID}"
  private const val STORAGE =
      "http://${FirebaseEmulator.HOST}:${FirebaseEmulator.STORAGE_PORT}/v0/b"
  private const val TIMEOUT_MILLIS = 5_000
  /** The answer of an emulator for something missing, e.g. a file already deleted. */
  private val NOT_FOUND = setOf(HttpURLConnection.HTTP_NOT_FOUND)
  /** The subcollections of a care circle, from the Firestore rules. */
  private val CIRCLE_SUBCOLLECTIONS = listOf("media", "stories", "appointments")

  /**
   * Gives a care circle its storage bucket, as the bucket trigger does once the circle is created.
   * Only that field is written: the rest of the circle stays as the app created it.
   *
   * @param circleId the circle's id
   * @param bucket the bucket's name, without `gs://`
   */
  fun setStorageBucket(circleId: String, bucket: String) {
    val document =
        JSONObject()
            .put("name", "$DATABASE/documents/careCircles/$circleId")
            .put(
                "fields",
                JSONObject().put("storageBucket", JSONObject().put("stringValue", bucket)),
            )
    val write =
        JSONObject()
            .put("update", document)
            .put("updateMask", JSONObject().put("fieldPaths", JSONArray(listOf("storageBucket"))))
            // fails rather than creating a circle that doesn't exist
            .put("currentDocument", JSONObject().put("exists", true))
    commit(listOf(write))
  }

  /**
   * Deletes a care circle's media, stories and appointments, then the circle document. Deleting a
   * document doesn't delete its subcollections, so they go first. No client may delete a circle.
   *
   * @param circleId the circle to delete
   */
  fun deleteCircle(circleId: String) {
    val circle = "careCircles/$circleId"
    val children = CIRCLE_SUBCOLLECTIONS.flatMap { documentNames("$circle/$it") }
    commit(children.map { JSONObject().put("delete", it) })
    commit(listOf(JSONObject().put("delete", "$DATABASE/documents/$circle")))
  }

  /**
   * Lists the documents of a collection, whatever the rules say. Tests use it to check what is
   * left.
   *
   * @param collection the collection's path, e.g. `careCircles/c1/media`
   * @return the full names of its documents; none if the collection is empty
   */
  fun documentNames(collection: String): List<String> {
    val body = request("GET", "$FIRESTORE/documents/$collection", missingCodes = NOT_FOUND)
    val documents = body?.let { JSONObject(it).optJSONArray("documents") } ?: return emptyList()
    return List(documents.length()) { documents.getJSONObject(it).getString("name") }
  }

  /**
   * Deletes the profiles at `users/{uid}`.
   *
   * @param uids the uids whose profiles to delete
   */
  fun deleteProfiles(uids: Collection<String>) {
    commit(uids.map { JSONObject().put("delete", "$DATABASE/documents/users/$it") })
  }

  /**
   * Deletes every file of the bucket. A test's bucket belongs to that test only.
   *
   * @param bucket the bucket's name, without `gs://`
   */
  fun deleteBucketFiles(bucket: String) {
    val listing = request("GET", "$STORAGE/$bucket/o", missingCodes = NOT_FOUND) ?: return
    val items = JSONObject(listing).optJSONArray("items") ?: return
    for (i in 0 until items.length()) {
      val name = items.getJSONObject(i).getString("name")
      request("DELETE", "$STORAGE/$bucket/o/${encode(name)}", missingCodes = NOT_FOUND)
    }
  }

  /**
   * Deletes the Auth emulator accounts with these uids. An account already gone is skipped: the
   * Auth emulator answers it with a 400 `USER_NOT_FOUND`. Any other error is thrown.
   *
   * @param uids the uids of the accounts to delete
   */
  fun deleteAccounts(uids: Collection<String>) {
    for (uid in uids) {
      try {
        request("POST", "$AUTH/accounts:delete", JSONObject().put("localId", uid))
      } catch (e: IOException) {
        if ("USER_NOT_FOUND" !in e.message.orEmpty()) throw e
      }
    }
  }

  /**
   * Applies these writes together, through the `commit` endpoint (a POST, which [HttpURLConnection]
   * supports, unlike the PATCH of a single document).
   *
   * @param writes the Firestore REST writes to apply; nothing is sent if there are none
   */
  private fun commit(writes: List<JSONObject>) {
    if (writes.isEmpty()) return
    request("POST", "$FIRESTORE/documents:commit", JSONObject().put("writes", JSONArray(writes)))
  }

  /**
   * Sends one admin request to an emulator.
   *
   * @param method the HTTP method
   * @param url the full URL, on [FirebaseEmulator.HOST]
   * @param body the JSON body to send, if any
   * @param missingCodes the error codes that only mean "already missing", e.g. deleting what is
   *   already gone
   * @return the answer's body, or null for one of [missingCodes]
   * @throws IOException if the emulator answers with an error
   */
  private fun request(
      method: String,
      url: String,
      body: JSONObject? = null,
      missingCodes: Set<Int> = emptySet(),
  ): String? {
    checkEmulatorProject()
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
      connection.requestMethod = method
      connection.connectTimeout = TIMEOUT_MILLIS
      connection.readTimeout = TIMEOUT_MILLIS
      connection.setRequestProperty("Authorization", "Bearer owner")
      if (body != null) {
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(body.toString().toByteArray()) }
      }
      val code = connection.responseCode
      if (code in missingCodes) return null
      if (code !in 200..299) {
        val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
        throw IOException("$method $url failed with $code: $error")
      }
      return connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
      connection.disconnect()
    }
  }

  /**
   * Refuses to go on until [FirebaseEmulator.useForDefaultApp] has run. The requests themselves
   * always go to the emulators, but before that call the app is still on the real project, so this
   * would set up or delete data the app can't see.
   *
   * @throws IllegalStateException if the default app is not on [FirebaseEmulator.PROJECT_ID]
   */
  private fun checkEmulatorProject() {
    val projectId = Firebase.app.options.projectId
    check(projectId == FirebaseEmulator.PROJECT_ID) {
      "Call FirebaseEmulator.useForDefaultApp() first: the app is on $projectId"
    }
  }

  /**
   * Encodes a URL path segment, with `%20` for spaces as paths need.
   *
   * @param value the text to encode
   * @return the encoded text
   */
  private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
