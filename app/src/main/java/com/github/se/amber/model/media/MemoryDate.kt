// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.media

/**
 * When a memory happened. Day and month are optional because families often only know the year
 * ("summer 1974"); filling them in gives a more precise timeline when they are known.
 *
 * Embedded in a MediaItem as a map {year, month, day}. Firestore returns these integers as Long:
 * read them with (x as? Long)?.toInt().
 */
data class MemoryDate(
    val year: Int = 0,
    val month: Int? = null, // 1..12
    val day: Int? = null, // 1..31, only meaningful when month is set
)
