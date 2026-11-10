// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by camomille299, with assistance from Claude (Anthropic) via claude.ai.

package com.github.se.amber.ui.patient

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.DISABLED_ALPHA
import com.github.se.amber.ui.theme.PATIENT_BUTTON_HEIGHT_MEDIUM
import com.github.se.amber.ui.theme.PATIENT_BUTTON_HEIGHT_SMALL
import com.github.se.amber.ui.theme.PATIENT_BUTTON_WIDTH
import com.github.se.amber.ui.theme.PATIENT_ICON_SIZE
import com.github.se.amber.ui.theme.PATIENT_PADDING_MEDIUM
import com.github.se.amber.ui.theme.PATIENT_PADDING_SMALL
import com.github.se.amber.ui.theme.ROUND_CORNER
import com.github.se.amber.ui.theme.WEIGHT_ONE

object PatientUtilsTestTags {
  const val TITLE = "PatientUtils_test_tag_title"
  const val BUTTON = "PatientUtils_test_tag_button"
  const val BUTTON_WITH_ICON = "PatientUtils_test_tag_button_with_icon"
  const val BUTTON_ICON = "PatientUtils_test_tag_button_icon"
  const val ICON_BUTTON = "PatientUtils_test_tag_icon_button"
}

/**
 * Displays the title for patient's Screen
 *
 * @param modifier modifier to apply to the text
 * @param title title to display
 * @param titleStyle typography style of the title
 * @param testTag test tag of the title
 */
@NonRestartableComposable
@Composable
fun DisplayPatientTitle(
    modifier: Modifier = Modifier,
    title: String = "",
    titleStyle: TextStyle = MaterialTheme.typography.headlineLarge,
    testTag: String = PatientUtilsTestTags.TITLE,
) {
  Text(
      text = title,
      color = MaterialTheme.colorScheme.primary,
      style = titleStyle,
      modifier =
          modifier
              .fillMaxWidth()
              .padding(top = PATIENT_PADDING_SMALL, bottom = PATIENT_PADDING_MEDIUM)
              .testTag(testTag),
      textAlign = TextAlign.Center,
  )
}

/**
 * Displays a button with a label, an optional icon, and a click action.
 *
 * This button needs to be displayed in Scrollable Screens only ! (because it contains a fixed
 * height)
 *
 * @param modifier modifier applied to the button
 * @param buttonHeight height of the button
 * @param buttonWidth fraction of the available width taken by the button. Ignored when [fillWidth]
 *   is false.
 * @param fillWidth if true, the button fills the available width (scaled by [buttonWidth]) and is
 *   centered. If false, the button only wraps its content.
 * @param text label displayed on the button.
 * @param textStyle typography style of the button label
 * @param icon icon displayed after the label. No icon is displayed when null.
 * @param description content description for the icon. Only used when [icon] is not null.
 * @param enabled say if the button is clickable or not
 * @param onClick callback invoked when the button is selected.
 * @param testTag test tag of the button. Defaults to [PatientUtilsTestTags.BUTTON_WITH_ICON] when
 *   an icon is displayed, and to [PatientUtilsTestTags.BUTTON] otherwise.
 * @param iconTestTag test tag of the icon. Only used when [icon] is not null.
 */
@NonRestartableComposable
@Composable
fun DisplayPatientButton(
    modifier: Modifier = Modifier,
    buttonHeight: Dp = PATIENT_BUTTON_HEIGHT_MEDIUM,
    buttonWidth: Float = PATIENT_BUTTON_WIDTH,
    fillWidth: Boolean = true,
    text: String = "",
    textStyle: TextStyle = MaterialTheme.typography.headlineMedium,
    icon: ImageVector? = null,
    description: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    testTag: String? = null,
    iconTestTag: String = PatientUtilsTestTags.BUTTON_ICON,
) {
  val sizeModifier =
      if (fillWidth) {
        Modifier.fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .fillMaxWidth(buttonWidth)
      } else Modifier
  val resolvedTestTag =
      testTag
          ?: if (icon != null) PatientUtilsTestTags.BUTTON_WITH_ICON
          else PatientUtilsTestTags.BUTTON
  Button(
      onClick = onClick,
      modifier = modifier.then(sizeModifier).height(buttonHeight).testTag(resolvedTestTag),
      enabled = enabled,
      shape = RoundedCornerShape(ROUND_CORNER),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.tertiary,
              contentColor = MaterialTheme.colorScheme.onTertiary,
          ),
  ) {
    Row(
        modifier = if (fillWidth) Modifier.fillMaxSize() else Modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          text,
          style = textStyle,
          textAlign = TextAlign.Center,
          modifier = if (fillWidth) Modifier.weight(WEIGHT_ONE) else Modifier,
      )
      icon?.let {
        Icon(
            imageVector = it,
            contentDescription = description,
            modifier =
                (if (fillWidth) Modifier else Modifier.padding(start = PATIENT_PADDING_SMALL))
                    .testTag(iconTestTag),
        )
      }
    }
  }
}

/**
 * Displays a circular patient button holding an icon, or a loading spinner.
 *
 * [DisplayPatientButton] always includes a text label and a width fraction, so an icon-only control
 * uses its own container color, content color, and height. The content description is set on the
 * button itself so that screen readers announce the button, not its icon.
 *
 * @param modifier modifier applied to the button
 * @param icon icon displayed on the button
 * @param description content description of the button
 * @param enabled say if the button is clickable or not
 * @param showSpinner replaces the icon with a loading spinner when true
 * @param onClick callback invoked when the button is selected
 * @param testTag test tag of the button
 * @param spinnerTestTag test tag of the spinner. No tag is set when empty.
 */
@Composable
fun DisplayPatientCircularIconButton(
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Add,
    description: String = "",
    enabled: Boolean = true,
    showSpinner: Boolean = false,
    onClick: () -> Unit = {},
    testTag: String = PatientUtilsTestTags.ICON_BUTTON,
    spinnerTestTag: String = "",
) {
  val contentColor = MaterialTheme.colorScheme.onTertiary
  FilledIconButton(
      onClick = onClick,
      enabled = enabled,
      modifier =
          modifier
              .size(PATIENT_BUTTON_HEIGHT_SMALL)
              .semantics { contentDescription = description }
              .testTag(testTag),
      shape = CircleShape,
      colors =
          IconButtonDefaults.filledIconButtonColors(
              containerColor = MaterialTheme.colorScheme.tertiary,
              contentColor = contentColor,
              disabledContainerColor =
                  MaterialTheme.colorScheme.tertiary.copy(alpha = DISABLED_ALPHA),
              disabledContentColor = contentColor.copy(alpha = DISABLED_ALPHA),
          ),
  ) {
    if (showSpinner) {
      CircularProgressIndicator(
          modifier =
              Modifier.size(PATIENT_ICON_SIZE)
                  .then(
                      if (spinnerTestTag.isNotEmpty()) Modifier.testTag(spinnerTestTag)
                      else Modifier
                  ),
          color = LocalContentColor.current,
      )
    } else {
      Icon(
          imageVector = icon,
          contentDescription = null,
          modifier = Modifier.size(PATIENT_ICON_SIZE),
      )
    }
  }
}

@Preview(showBackground = true, name = "Patient utils light")
@Composable
internal fun DisplayPatientButtonsPreview() {
  AmberAppTheme(darkTheme = false) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(PATIENT_PADDING_SMALL),
        verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      DisplayPatientButton(
          text = "Button",
          onClick = {},
          modifier = Modifier,
      )
      DisplayPatientButton(
          text = "Add",
          icon = Icons.Default.Add,
          description = "Add patient",
          fillWidth = false,
          onClick = {},
      )
      DisplayPatientCircularIconButton(
          description = "Add patient",
          onClick = {},
      )
    }
  }
}

@Preview(
    showBackground = true,
    name = "Patient utils dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
internal fun DisplayPatientButtonsDarkPreview() {
  AmberAppTheme(darkTheme = true) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(PATIENT_PADDING_SMALL),
        verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      DisplayPatientButton(
          text = "Button",
          onClick = {},
          modifier = Modifier,
      )
      DisplayPatientButton(
          text = "Add",
          icon = Icons.Default.Add,
          description = "Add patient",
          fillWidth = false,
          onClick = {},
      )
      DisplayPatientCircularIconButton(
          description = "Add patient",
          onClick = {},
      )
    }
  }
}
