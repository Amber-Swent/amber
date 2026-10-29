// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
// Modified with assistance from OpenAI Codex.
package com.github.se.amber.data.media

import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaStatus
import com.github.se.amber.model.media.MemoryDate
import java.time.DateTimeException
import java.time.Month

/*
 * How a MediaItem is stored, in its Firestore document and in the outbox (MediaOutbox): a map of
 * plain values (strings, numbers, lists of strings, nested maps), with a "type" field telling which
 * subclass it is. The id isn't in the map: it is the document's id, or the outbox file's name.
 *
 * Reading accepts any kind of number for a number field: Firestore gives integers as Long, and
 * JSON gives back a whole Double (e.g. a latitude of 46.0) as an integer.
 */

private const val TYPE = "type"
private const val TYPE_PICTURE = "picture"
private const val TYPE_AUDIO = "audio"
private const val TYPE_TEXT = "text"

/**
 * This media's stored fields, as a map of plain values; its [MediaItem.id] isn't included. A null
 * [MediaItem.date] or [MediaItem.location] is stored as null.
 */
fun MediaItem.toMap(): Map<String, Any?> {
  val common =
      mapOf(
          "authorId" to authorId,
          "storagePath" to storagePath,
          "createdAt" to createdAt,
          "description" to description,
          "date" to date?.let { mapOf("year" to it.year, "month" to it.month?.value, "day" to it.day) },
          "location" to
              location?.let {
                mapOf("lat" to it.lat, "lng" to it.lng, "placeName" to it.placeName)
              },
          "shownIds" to shownIds,
          "authorizedIds" to authorizedIds,
          "status" to status.name,
      )
  val own =
      when (this) {
        is MediaItem.Picture -> mapOf(TYPE to TYPE_PICTURE, "width" to width, "height" to height)
        is MediaItem.Audio ->
            mapOf(TYPE to TYPE_AUDIO, "durationMs" to durationMs, "transcript" to transcript)
        is MediaItem.Text -> mapOf(TYPE to TYPE_TEXT, "text" to text)
      }
  return common + own
}

/**
 * Rebuilds the media [id] from [map], as written by [toMap]. A missing field takes its default
 * value, like Firestore's toObject() would give it.
 *
 * Returns null if the media can't be read: its "type" is missing or unknown (e.g. "video" from a
 * newer app version), or a field has the wrong kind of value (e.g. a text where a number is
 * expected, or an unknown status). The caller then skips it.
 */
fun mediaItemFromMap(id: String, map: Map<String, Any?>): MediaItem? =
    try {
      Fields(map).read(id)
    } catch (_: BadFieldException) {
      null
    }

/** A field of a stored media holds the wrong kind of value. */
private class BadFieldException(key: String) : Exception("Bad media field: $key")

/** Reads the fields of [map], throwing [BadFieldException] for one of the wrong kind. */
private class Fields(private val map: Map<String, Any?>) {

  fun read(id: String): MediaItem? {
    val authorId = string("authorId")
    val storagePath = string("storagePath")
    val createdAt = long("createdAt")
    val description = string("description")
    val date = date()
    val location = location()
    val shownIds = strings("shownIds")
    val authorizedIds = strings("authorizedIds")
    val status = status()
    return when (map[TYPE]) {
      TYPE_PICTURE ->
          MediaItem.Picture(
              id = id,
              authorId = authorId,
              storagePath = storagePath,
              createdAt = createdAt,
              description = description,
              date = date,
              location = location,
              shownIds = shownIds,
              authorizedIds = authorizedIds,
              status = status,
              width = int("width"),
              height = int("height"),
          )
      TYPE_AUDIO ->
          MediaItem.Audio(
              id = id,
              authorId = authorId,
              storagePath = storagePath,
              createdAt = createdAt,
              description = description,
              date = date,
              location = location,
              shownIds = shownIds,
              authorizedIds = authorizedIds,
              status = status,
              durationMs = long("durationMs"),
              transcript = stringOrNull("transcript"),
          )
      TYPE_TEXT ->
          MediaItem.Text(
              id = id,
              authorId = authorId,
              storagePath = storagePath,
              createdAt = createdAt,
              description = description,
              date = date,
              location = location,
              shownIds = shownIds,
              authorizedIds = authorizedIds,
              status = status,
              text = string("text"),
          )
      else -> null // missing or unknown type
    }
  }

  private fun date(): MemoryDate? {
    val date = nested("date") ?: return null
    return MemoryDate(
        year = date.int("year"),
        month =
            date.intOrNull("month")?.let { value ->
              try {
                Month.of(value)
              } catch (_: DateTimeException) {
                throw BadFieldException("month")
              }
            },
        day = date.intOrNull("day"),
    )
  }

  private fun location(): GeoLocation? {
    val location = nested("location") ?: return null
    return GeoLocation(
        lat = location.double("lat"),
        lng = location.double("lng"),
        placeName = location.stringOrNull("placeName"),
    )
  }

  private fun status(): MediaStatus {
    // MediaItem's own default, so it follows when moderation switches that default to PENDING
    val name = stringOrNull("status") ?: return MediaItem.Text().status
    return MediaStatus.entries.find { it.name == name } ?: throw BadFieldException("status")
  }

  fun string(key: String): String = stringOrNull(key) ?: ""

  fun stringOrNull(key: String): String? =
      when (val value = map[key]) {
        null -> null
        is String -> value
        else -> throw BadFieldException(key)
      }

  fun long(key: String): Long = longOrNull(key) ?: 0L

  fun int(key: String): Int = intOrNull(key) ?: 0

  fun intOrNull(key: String): Int? {
    val value = longOrNull(key) ?: return null
    if (value !in Int.MIN_VALUE..Int.MAX_VALUE) throw BadFieldException(key)
    return value.toInt()
  }

  /** A whole number, given as any kind of number; a fraction is a bad field. */
  private fun longOrNull(key: String): Long? {
    val value = map[key] ?: return null
    val whole =
        when (value) {
          is Long,
          is Int,
          is Short,
          is Byte -> (value as Number).toLong()
          is Number -> value.toDouble().takeIf { it % 1.0 == 0.0 }?.toLong()
          else -> null
        }
    return whole ?: throw BadFieldException(key)
  }

  fun double(key: String): Double =
      when (val value = map[key]) {
        null -> 0.0
        is Number -> value.toDouble()
        else -> throw BadFieldException(key)
      }

  fun strings(key: String): List<String> =
      when (val value = map[key]) {
        null -> emptyList()
        is List<*> -> value.map { it as? String ?: throw BadFieldException(key) }
        else -> throw BadFieldException(key)
      }

  /** The nested map [key] (a date or a location), or null if absent. */
  private fun nested(key: String): Fields? =
      when (val value = map[key]) {
        null -> null
        is Map<*, *> -> Fields(value.entries.associate { (k, v) -> k.toString() to v })
        else -> throw BadFieldException(key)
      }
}
