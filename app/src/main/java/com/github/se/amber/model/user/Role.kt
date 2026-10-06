// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.user

/**
 * What a user can do in their circle. Stored as its name ("PATIENT" / "CAREGIVER"); toObject()
 * converts it back automatically.
 */
enum class Role {
  PATIENT,
  CAREGIVER,
}
