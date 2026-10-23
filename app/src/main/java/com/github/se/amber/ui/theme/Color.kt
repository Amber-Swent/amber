/** Written by Lou-Anne Maier */
package com.github.se.amber.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * App's primary colors
 *
 * Shades of orange are the dominant colors in the Amber app. They are use for lightMode. Shades of
 * blue are used to provide contrast and for the darkMode.
 */

/**
 * For : Empty Space that shouldn't be (example : no Text/Picture/Audio upload)
 *
 * Note: This isn't one of the app's primary colors, but it's the color used in Figma for areas that
 * will be filled with pictures
 */
val backOrange = Color(0xFFFFE6CE)

/** In lightMode, for : Unselected Tab Button colors */
val lightOrange = Color(0xFFFBB05D)

/** In lightMode, for : Selected Tab Title text of secondaryView */
val orange = Color(0xFFCE5431)

/**
 * In lightMode, for : Main Title Text of the top navigation bar and the corresponding back arrow
 * (example : Checkout, Cancel)
 */
val darkOrange = Color(0xFF9D3121)

/** In darkMode, for : Secondary Title selected icon */
val lightCyan = Color(0xFFB8DEC1)

/** In darkMode, for : Main Title */
val cyan = Color(0xFF89C9BE)

/** In darkMode, for : Background */
val darkCyan = Color(0xFF255556)

/**
 * In light Mode, for : Background boxes border
 *
 * * In darkMode, for : Button, unselected tab
 */
val lightGray = Color(0xFFE6E6E6)

/** In lightMode, for : Background text */
val darkGray = Color(0xFF626262)
