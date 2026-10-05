// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.media

/**
 * Moderation state. Stored as its name. The patient only sees APPROVED media. Default is APPROVED
 * until moderation exists; switch the default to PENDING when it does.
 */
enum class MediaStatus {
  PENDING,
  APPROVED,
  REJECTED,
}
