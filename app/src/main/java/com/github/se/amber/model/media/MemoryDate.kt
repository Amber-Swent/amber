// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
// Modified with assistance from OpenAI Codex.
package com.github.se.amber.model.media

import java.time.Month

/**
 * When a memory happened. Day and month are optional because families often only know the year
 * ("summer 1974"); filling them in gives a more precise timeline when they are known.
 *
 * Embedded in a MediaItem as a map {year, month, day}. The month is stored as its number (1 to 12)
 * and converted back to [Month] when read.
 */
data class MemoryDate(
    val year: Int = 0,
    val month: Month? = null,
    val day: Int? = null, // 1..31, only meaningful when month is set
)
