package com.github.se.amber.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Amber Color Scheme for dark Mode
 *
 * If an element doesn't fit inside one of the description, use the color define in [Color]
 */
private val DarkColorScheme =
    darkColorScheme(
        background = darkCyan,
        onBackground = Color.White,

        /** Main Title */
        primary = cyan,
        onPrimary = Color.Black,

        /** Secondary Title, selected icon */
        secondary = lightCyan,
        onSecondary = darkCyan,

        /** Button, unselected icon */
        tertiary = lightGray,
        onTertiary = darkCyan,

        surface = darkCyan,
        onSurface = Color.White,

        error = Color.White,
    )

/** Amber Color Scheme for light Mode
 *
 * If an element doesn't fit inside one of the description, use the color define in [Color]
 */
private val LightColorScheme =
    lightColorScheme(
        background = Color.White,
        onBackground = Color.Black,

        /** Main Title */
        primary = darkOrange,
        onPrimary = Color.Black,

        /** Secondary Title, selected icon */
        secondary = orange,
        onSecondary = Color.Black,

        /** Button, unselected icon */
        tertiary = lightOrange,
        onTertiary = Color.Black,

        surface = Color.White,
        onSurface = Color.Black,

        error = darkOrange,
    )

@Composable
fun AmberAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,     // Dynamic color is available on Android 12+
    content: @Composable () -> Unit,
) {
  val colorScheme =
      when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
          val context = LocalContext.current
          if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
      }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                !darkTheme
        }
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
