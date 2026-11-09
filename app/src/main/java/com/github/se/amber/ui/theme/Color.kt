// Written by LaMaier
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * App's primary colors
 *
 * Shades of orange are the dominant colors in the Amber app. They are use for lightMode. Shades of
 * blue are used to provide contrast and for the darkMode.
 */

/**
 * In lightMode, for : selected chips
 *
 * Note: It's the color used in Figma for areas that will be filled with pictures.
 */
val backOrange = Color(0xFFFFCF9D)

/** In lightMode, for : Unselected Tab Button colors */
val lightOrange = Color(0xFFFBB05D)

/** In lightMode, for : Selected Tab Title text of secondaryView */
val orange = Color(0xFFCE5431)

/**
 * In lightMode, for : Main Title Text of the top navigation bar and the corresponding back arrow
 * (example : Sign out, Cancel)
 */
val darkOrange = Color(0xFF9D3121)

/** In darkMode, for : selected chips */
val backCyan = Color(0xFF8FD789)

/** In darkMode, for : Secondary Title selected icon */
val lightCyan = Color(0xFFA6D9B2)

/** In darkMode, for : Main Title */
val cyan = Color(0xFF94F5D6)

/** In darkMode, for : Background */
val darkCyan = Color(0xFF255556)

/**
 * In light Mode, for : Background boxes border, unselected chips
 *
 * * In darkMode, for : Button, unselected tab, unselected chips
 */
val lightGray = Color(0xFFE6E6E6)

/** In lightMode, for : Background text */
val darkGray = Color(0xFF626262)
