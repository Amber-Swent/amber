// Written by Alaabrine, with assistance from Cursor Agent.

package com.github.se.amber.emulator

import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** Base URL of the Firestore emulator, from `FIRESTORE_EMULATOR_HOST` when it is set. */
internal fun firestoreEmulatorBaseUrl(): String =
    System.getenv("FIRESTORE_EMULATOR_HOST")?.let { "http://$it" } ?: "http://127.0.0.1:8080"

/** Base URL of the Storage emulator, from `FIREBASE_STORAGE_EMULATOR_HOST` when it is set. */
internal fun storageEmulatorBaseUrl(): String =
    System.getenv("FIREBASE_STORAGE_EMULATOR_HOST")?.let { "http://$it" } ?: "http://127.0.0.1:9199"

/** True when [baseUrl] accepts an HTTP connection, so a rules test can decide to run. */
internal fun emulatorAcceptsHttp(baseUrl: String): Boolean =
    try {
      val connection =
          (URL("$baseUrl/").openConnection() as HttpURLConnection).apply {
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

/** Minimal Firestore emulator REST client with per-request auth uid. */
internal class FirestoreEmulatorClient(
    private val projectId: String = "amber-34abf",
    private val host: String = firestoreEmulatorBaseUrl(),
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
                """{"iss":"https://securetoken.google.com/$projectId","aud":"$projectId","iat":1700000000,"exp":1800000000,"sub":"$uid","user_id":"$uid","firebase":{"sign_in_provider":"custom"}}""".toByteArray(),
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
      fields.mapValues { (_, value) -> encodeValue(value) }

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
            mapOf("mapValue" to mapOf("fields" to encodeFields(value as Map<String, Any?>)))
        else -> error("Unsupported Firestore value: ${value::class} = $value")
      }

  private fun jsonEncode(value: Any?): String =
      when (value) {
        null -> "null"
        is String -> "\"${value.replace("\"", "\\\"")}\""
        is Number, is Boolean -> value.toString()
        is Map<*, *> ->
            value.entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\":${jsonEncode(v)}" }
        is List<*> -> value.joinToString(",", "[", "]") { jsonEncode(it) }
        else -> error("Cannot JSON-encode ${value::class}")
      }

  private companion object {
    const val REQUEST_TIMEOUT_MS = 5_000
  }
}
