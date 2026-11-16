// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data

/**
 * Whether [id] can be used as the id of one Firestore document.
 *
 * A valid id isn't blank and has no spaces around it. It has no '/' either: a '/' would make
 * `document()` resolve to a nested path, so the call would read or write a different document.
 */
fun isValidDocumentId(id: String): Boolean = id.isNotBlank() && id == id.trim() && '/' !in id
