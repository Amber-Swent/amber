package com.github.se.amber.ui.theme

import android.R.id.primary
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.se.amber.model.media.MediaItem

/** App's Constant value
 *
 * E.g. padding, size and any other "Magic Number"
 */


// ------- PADDING -------
val PADDING_MINI = 4.dp

val CAREGIVER_PADDING_SMALL = 8.dp
val CAREGIVER_PADDING_MEDIUM = 16.dp
val CAREGIVER_PADDING_BIG = 24.dp
val CAREGIVER_PADDING_ENORMOUS = 48.dp

val PATIENT_PADDING_SMALL = 16.dp
val PATIENT_PADDING_MEDIUM = 24.dp
val PATIENT_PADDING_BIG = 32.dp
val PATIENT_PADDING_ENORMOUS = 64.dp

/** Preview of padding
 * Useful to visualize each value
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
                modifier = Modifier.padding(CAREGIVER_PADDING_MEDIUM)
            )
        }
    )
}

// ------- BOX -------
val ROUND_CORNER = 12.dp
val DIVIDER_THICKNESS = 1.dp
val BORDER_THICKNESS = 1.dp

// ------- TEXT_FIELD -------
val TEXT_FIELD_SMALL = 50.dp
val TEXT_FIELD_DEFAULT = 70.dp
val TEXT_FIELD_BIG = 200.dp

// ------- MAX -------

const val MAX_LINE_DEFAULT = 2
const val MAX_LINE_DESCRIPTION = 5

const val MAX_CHAR_DEFAULT = 20
const val MAX_CHAR_DESCRIPTION = 200

// ------- SURFACE -------

val SURFACE_SMALL = 24.dp
val SURFACE_MEDIUM = 48.dp
val SURFACE_BIG = 84.dp

// ------- LOGO -------
val LOGO_SIZE = 48.dp

// ------- CAREGIVER BUTTON -------
val CAREGIVER_BUTTON_HEIGHT_SMALL = 32.dp
val CAREGIVER_BUTTON_HEIGHT_MEDIUM = 52.dp
val CAREGIVER_BUTTON_HEIGHT_BIG = 64.dp

// It may be necessary to add some Small, Medium, Big Wight.
// If it's the case, do a specific Pr for this
const val CAREGIVER_BUTTON_WIGHT = 0.5f
/** Preview of CaregiverButtonSize using the Amber application theme. */
@Preview(showBackground = true)
@Composable
private fun CaregiverButtonSizePreview() {
    AmberAppTheme(
        darkTheme = false,
        dynamicColor = false,
    content = {
        Button(
        onClick = { },
        modifier = Modifier
            .height(CAREGIVER_BUTTON_HEIGHT_MEDIUM)
            .fillMaxWidth(CAREGIVER_BUTTON_WIGHT),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ){
        Icon(imageVector = Icons.Outlined.Home, contentDescription = "Home", tint = Color.Black)
        }
    }
    )
}

// ------- PATIENT BUTTON -------
val PATIENT_BUTTON_HEIGHT_SMALL = 52.dp
val PATIENT_BUTTON_HEIGHT_MEDIUM = 96.dp
val PATIENT_BUTTON_HEIGHT_BIG = 120.dp

// It may be necessary to add some Small, Medium, Big Wight.
// If it's the case, do a specific Pr for this
const val PATIENT_BUTTON_WIGHT = 0.7f

/** Preview of PatientButtonSize using the Amber application theme. */
@Preview(showBackground = true)
@Composable
private fun PatientButtonSizePreview() {
    AmberAppTheme(
        darkTheme = false,
        dynamicColor = false,
        content = {
            Button(
                onClick = { },
                modifier = Modifier
                    .height(PATIENT_BUTTON_HEIGHT_MEDIUM)
                    .fillMaxWidth(PATIENT_BUTTON_WIGHT),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ){
                Icon(imageVector = Icons.Outlined.Home, contentDescription = "Home", tint = Color.Black)
            }
        }
    )
}