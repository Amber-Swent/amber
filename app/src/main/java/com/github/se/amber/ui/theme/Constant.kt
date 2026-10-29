/**
 * Written by Lou-Anne Maier. Modified by nehachak1, with assistance from Claude Code. Modified by
 * Yuhan YANG, with assistance from Cursor.
 */
package com.github.se.amber.ui.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * App's Constant value
 *
 * E.g. padding, size and any other "Magic Number"
 */

// ------- PADDING -------
val PADDING_MINI = 4.dp

val CAREGIVER_PADDING_SMALL = 8.dp
val CAREGIVER_PADDING_REGULAR = 12.dp
val CAREGIVER_PADDING_MEDIUM = 16.dp
val CAREGIVER_PADDING_BIG = 24.dp
val CAREGIVER_PADDING_ENORMOUS = 48.dp

val PATIENT_PADDING_SMALL = 16.dp
val PATIENT_PADDING_MEDIUM = 24.dp
val PATIENT_PADDING_BIG = 32.dp
val PATIENT_PADDING_ENORMOUS = 64.dp

/**
 * Preview of padding. Useful to visualize each value
 *
 * The CAREGIVER_PADDING_MEDIUM is a default value.
 */
@Preview(showBackground = true)
@Composable
private fun PaddingPreview() {
  AmberAppTheme(
      content = {
        Text(
            text = "Hello",
            modifier = Modifier.padding(CAREGIVER_PADDING_MEDIUM),
        )
      }
  )
}

// ------- CAREGIVER BUTTON -------
val CAREGIVER_BUTTON_HEIGHT_SMALL = 32.dp
val CAREGIVER_BUTTON_HEIGHT_MEDIUM = 52.dp
val CAREGIVER_BUTTON_HEIGHT_BIG = 64.dp

// It may be necessary to add some Small, Medium, Big Width.
// If it's the case, do a specific Pr for this and remove this comment
const val CAREGIVER_BUTTON_WIDTH = 0.5f

// ------- PATIENT BUTTON -------
val PATIENT_BUTTON_HEIGHT_SMALL = 52.dp
val PATIENT_BUTTON_HEIGHT_MEDIUM = 96.dp
val PATIENT_BUTTON_HEIGHT_BIG = 120.dp

// It may be necessary to add some Small, Medium, Big Width.
// If it's the case, do a specific Pr for this and remove this comment
const val PATIENT_BUTTON_WIDTH = 0.7f

// ------- BOX -------
val ROUND_CORNER = 12.dp
val DIVIDER_THICKNESS = 1.dp
val BORDER_THICKNESS = 1.dp

// ------- SURFACE -------

val SURFACE_SMALL = 24.dp
val SURFACE_MEDIUM = 48.dp
val SURFACE_BIG = 84.dp

// ------- TEXT_FIELD -------
// 56.dp is the minimum height required for the letters to be displayed in full
val TEXT_FIELD_SMALL = 56.dp
val TEXT_FIELD_DEFAULT = 70.dp
val TEXT_FIELD_BIG = 200.dp

// ------- MAX -------

const val MAX_LINE_DEFAULT = 2
const val MAX_LINE_DESCRIPTION = 5

const val MAX_CHAR_DEFAULT = 20
const val MAX_CHAR_DESCRIPTION = 200

// ------- WEIGHT -------
const val WEIGHT_ONE = 1f

const val WEIGHT_TWO = 2f

// ------- LOGO -------
val LOGO_SIZE = 48.dp

// ------- NAVIGATION ICON -------
/** Icon size for caregiver tabs. */
val CAREGIVER_ICON_SIZE = 24.dp

/** Icon size for patient tabs. */
val PATIENT_ICON_SIZE = 32.dp
