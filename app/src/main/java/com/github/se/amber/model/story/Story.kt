// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.story

/**
 * A grouping of existing media. Stored at careCircles/{circleId}/stories/{id}. Holds only media
 * ids, never copies of the media.
 *
 * Written: created once; any member of the circle can then edit it. Adding or removing a media item
 * only changes [mediaIds], with no re-upload, and should change only the ids being added or
 * removed, not rewrite the whole list, so concurrent edits by two members don't overwrite each
 * other. Reordering is the exception: it rewrites the whole list.
 *
 * Read with toObject(). Its media are then fetched by id (query media where documentId in mediaIds,
 * at most 30 ids per query) or taken from media already loaded. Ids of deleted media are simply
 * skipped.
 */
data class Story(
    val id: String = "", // storyId = document id
    val title: String = "",
    val description: String = "",
    val createdBy: String = "", // uid of the member who created it
    // mediaIds in the same circle; list order = display order
    val mediaIds: List<String> = emptyList(),
    val createdAt: Long = 0L, // Optional; epoch milliseconds
)
