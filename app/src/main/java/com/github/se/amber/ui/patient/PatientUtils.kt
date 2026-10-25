// this code was written with the aid of AI

package com.github.se.amber.ui.patient

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.github.se.amber.ui.theme.PADDING_ENORMOUS
import com.github.se.amber.ui.theme.PADDING_MEDIUM
import com.github.se.amber.ui.theme.PADDING_SMALL

object PatientUtilsTestTags {
  const val TITLE = "PatientUtils_test_tag_title"
  const val BUTTON = "PatientUtils_test_tag_button"
  const val BUTTON_WITH_ICON = "PatientUtils_test_tag_button_with_icon"
  const val BUTTON_ICON = "PatientUtils_test_tag_button_icon"
}

/**
 * Displays the title for patient's Screen
 *
 * @param title title to display
 * @param modifier modifier to apply to the text
 * @param testTag test tag of the title
 */
@Composable
fun DisplayPatientTitle(
    title: String = "",
    modifier: Modifier = Modifier,
    testTag: String = PatientUtilsTestTags.TITLE,
) {
  Text(
      text = title,
      color = MaterialTheme.colorScheme.primary,
      style = MaterialTheme.typography.headlineSmall,
      modifier =
          modifier
              .fillMaxWidth()
              .padding(top = PADDING_SMALL, bottom = PADDING_MEDIUM)
              .testTag(testTag),
      textAlign = TextAlign.Center,
  )
}

/**
 * Displays a button with an icon, label, and click action.
 *
 * @param modifier modifier applied to the button
 * @param text label displayed on the button.
 * @param icon icon displayed on the button.
 * @param description content description for the icon.
 * @param onClick callback invoked when the button is selected.
 * @param testTag test tag of the button
 * @param iconTestTag test tag of the icon
 */
@Composable
fun DisplayPatientButtonWithIcon(
    modifier: Modifier = Modifier,
    text: String = "",
    icon: ImageVector = Icons.Default.Add,
    description: String = "",
    onClick: () -> Unit = {},
    testTag: String = PatientUtilsTestTags.BUTTON_WITH_ICON,
    iconTestTag: String = PatientUtilsTestTags.BUTTON_ICON,
) {
  Button(
      onClick = onClick,
      modifier = modifier.height(PADDING_ENORMOUS).testTag(testTag),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.tertiary,
              contentColor = MaterialTheme.colorScheme.onTertiary,
          ),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          text,
          style = MaterialTheme.typography.bodyLarge,
          textAlign = TextAlign.Center,
          modifier = Modifier.weight(1f),
      )
      Icon(
          icon,
          contentDescription = description,
          modifier = Modifier.testTag(iconTestTag),
      )
    }
  }
}

/**
 * Displays a button, label, and click action.
 *
 * @param modifier modifier applied to the button
 * @param text label displayed on the button.
 * @param onClick callback invoked when the button is selected.
 * @param testTag test tag of the button
 */
@Composable
fun DisplayPatientButton(
    modifier: Modifier = Modifier,
    text: String = "",
    onClick: () -> Unit = {},
    testTag: String = PatientUtilsTestTags.BUTTON,
) {
  Button(
      onClick = onClick,
      modifier = modifier.height(PADDING_ENORMOUS).testTag(testTag),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.tertiary,
              contentColor = MaterialTheme.colorScheme.onTertiary,
          ),
  ) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
  }
}

// @Preview(showBackground = true)
// @Composable
// private fun DisplayPatientTitlePreview() {
//  AmberAppTheme(dynamicColor = false) {
//    DisplayPatientTitle(title = "Title Example", modifier = Modifier)
//  }
// }
//
// @Preview(showBackground = true)
// @Composable
// private fun DisplayButtonWithIconPreview() {
//  AmberAppTheme(dynamicColor = false) {
//    DisplayPatientButtonWithIcon(
//        modifier = Modifier.fillMaxWidth().padding(PADDING_MEDIUM),
//        icon = Icons.Default.Add,
//        text = "Button with icon example",
//        description = "click on the button",
//        onClick = {},
//    )
//  }
// }
//
// @Preview(showBackground = true)
// @Composable
// private fun DisplayButtonPreview() {
//  AmberAppTheme(dynamicColor = false) {
//    DisplayPatientButton(
//        modifier = Modifier.fillMaxWidth().padding(PADDING_MEDIUM),
//        text = "Button example",
//        onClick = {},
//    )
//  }
// }
