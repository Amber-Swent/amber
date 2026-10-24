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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.github.se.amber.ui.theme.PADDING_ENORMOUS
import com.github.se.amber.ui.theme.PADDING_MEDIUM
import com.github.se.amber.ui.theme.PADDING_SMALL
import com.github.se.amber.ui.theme.lightOrange
import com.github.se.amber.ui.theme.orange

/**
 * Displays the title for patient's Screen
 *
 * @param title title to display
 * @param modifier modifier to apply to the text (can for e.g. contains the necessary TestTag)
 */
@Composable
fun DisplayPatientTitle(title: String, modifier: Modifier) {
  Text(
      text = title,
      color = orange,
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
      modifier = modifier.fillMaxWidth().padding(top = PADDING_SMALL, bottom = PADDING_MEDIUM),
      textAlign = TextAlign.Center,
  )
}

/**
 * Displays a button with an icon, label, and click action.
 *
 * @param modifier modifier applied to the button
 * @param icon icon displayed on the button.
 * @param text label displayed on the button.
 * @param description content description for the icon.
 * @param onClick callback invoked when the button is selected.
 */
@Composable
fun DisplayButtonWithIcon(
    modifier: Modifier,
    icon: ImageVector,
    text: String,
    description: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(PADDING_ENORMOUS),
        colors = ButtonDefaults.buttonColors(containerColor = lightOrange),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                color = Color.Black,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Icon(
                icon,
                contentDescription = description,
                tint = Color.Black,
            )
        }
    }
}


@Preview(showBackground = true)
@Composable
private fun DisplayPatientTitlePreview() {
    DisplayPatientTitle(title = "Title Example", modifier = Modifier)
}

@Preview(showBackground = true)
@Composable
private fun DisplayButtonWithIconPreview() {
    DisplayButtonWithIcon(
        modifier = Modifier.fillMaxWidth().padding(PADDING_MEDIUM),
        icon = Icons.Default.Add,
        text = "Button example",
        description = "click on the button",
        onClick = {},
    )
}
