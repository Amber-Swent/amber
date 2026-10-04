// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.media

/**
 * A memory: one photo or voice memo, with its own description, date, place and people. Exists on
 * its own; it can be added to zero, one or several stories.
 *
 * Stored in two places with the same mediaId:
 * - bytes in Firebase Storage at [storagePath] (careCircles/{circleId}/media/{mediaId}.jpg|.m4a);
 *   the circleId in the path lets Storage security rules check membership.
 * - metadata at careCircles/{circleId}/media/{mediaId}, with an extra "type" field ("picture" or
 *   "audio") telling which subclass to rebuild.
 *
 * Written: generate mediaId, upload the file to Storage first, then write this document. That order
 * guarantees a document never points at a missing file. Storage uploads aren't queued across app
 * restarts like Firestore writes are, so offline uploads are retried in the background. Only the
 * uploader ([authorId]) can edit or delete it.
 *
 * Read:
 * - patient feed: query media where status == "APPROVED".
 * - "photos of X": query media where shownIds array-contains X's id.
 * - the file itself: Storage reference at storagePath, loaded only when its card is on screen.
 *
 * A sealed interface can't be read with toObject(): the repository maps the document manually,
 * switching on "type", and skips unknown types (e.g. "video" from a newer app version).
 */
sealed interface MediaItem {
  val id: String // mediaId = document id; the Storage file is named {mediaId}.jpg or .m4a
  val authorId: String // uid of the uploader (caregiver or patient)
  val storagePath: String // full Storage path of the file; never the bytes themselves
  val createdAt: Long // time of upload, epoch milliseconds
  val description: String
  val date: MemoryDate? // when it happened; null = unknown
  val location: GeoLocation? // where it happened; null = unknown
  val shownIds: List<String> // who is credited: member uids or CareCircle.people keys (personIds)
  // TODO: subject to change. Uids allowed to see it; empty = whole circle
  val authorizedIds: List<String>
  val status: MediaStatus

  data class Picture(
      override val id: String = "",
      override val authorId: String = "",
      override val storagePath: String = "",
      override val createdAt: Long = 0L,
      override val description: String = "",
      override val date: MemoryDate? = null,
      override val location: GeoLocation? = null,
      override val shownIds: List<String> = emptyList(),
      override val authorizedIds: List<String> = emptyList(),
      override val status: MediaStatus = MediaStatus.APPROVED,
      val width: Int = 0, // pixels, of the stored file; lets the UI reserve space before loading
      val height: Int = 0,
  ) : MediaItem

  data class Audio(
      override val id: String = "",
      override val authorId: String = "",
      override val storagePath: String = "",
      override val createdAt: Long = 0L,
      override val description: String = "",
      override val date: MemoryDate? = null,
      override val location: GeoLocation? = null,
      override val shownIds: List<String> = emptyList(),
      override val authorizedIds: List<String> = emptyList(),
      override val status: MediaStatus = MediaStatus.APPROVED,
      val durationMs: Long = 0L,
      val transcript: String? = null, // filled later by the AI
  ) : MediaItem
}
