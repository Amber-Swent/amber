// This file was written with the aid of AI
package com.github.se.amber.ui.authentication.signIn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.github.se.amber.ui.theme.PADDING_BIG
import com.github.se.amber.ui.theme.PADDING_ENORMOUS
import com.github.se.amber.ui.theme.PADDING_MINI
import com.github.se.amber.ui.theme.PADDING_REGULAR
import com.github.se.amber.ui.theme.SMALL_ROUND_CORNER
import com.github.se.amber.ui.theme.SURFACE_BIG
import com.github.se.amber.ui.theme.darkGray
import com.github.se.amber.ui.theme.lightGray
import com.github.se.amber.ui.theme.lightOrange

internal object AuthenticationComponentTestTags {
  const val HEADER = "authenticationHeader"
  const val TERMS_FOOTER = "authenticationTermsFooter"
}

/**
 * Provides the common layout shared by all authentication screens.
 *
 * The layout displays the Amber authentication header at the top, screen-specific [content] in the
 * center, and the terms and privacy notice at the bottom.
 *
 * @param modifier Modifier applied to the root layout.
 * @param spacing Vertical space between screen-specific content groups.
 * @param content Screen-specific composable content displayed in the center of the layout.
 */
@Composable
internal fun AuthenticationLayout(
    modifier: Modifier = Modifier,
    spacing: Dp,
    content: @Composable () -> Unit,
) {
  // Surface will be the background/container for the screen
  Surface(
      modifier = modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background,
      contentColor = MaterialTheme.colorScheme.onBackground,
  ) {
    // Use Box, a Column would stack every items sequentially
    Box(
        modifier =
            Modifier.fillMaxSize()
                .statusBarsPadding() // Avoids drawing underneath Android's top system bar
                .navigationBarsPadding() // Avoid drawing underneath navigation area at the bottom
                .padding(horizontal = PADDING_BIG),
    ) {
      AuthenticationHeader(
          modifier = Modifier.align(Alignment.TopCenter).padding(top = PADDING_ENORMOUS),
      )

      Column(
          modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(spacing), // Remove abusive use of Spacers
          content = { content() },
      )

      TermsFooter(
          modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = PADDING_MINI),
      )
    }
  }
}

/**
 * Displays the common header used by the authentication screens.
 *
 * The header contains the Amber application logo and application name.
 *
 * @param modifier Modifier applied to the header container.
 */
@Composable
internal fun AuthenticationHeader(
    modifier: Modifier = Modifier,
) {
  Column(
      modifier = modifier.testTag(AuthenticationComponentTestTags.HEADER),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(PADDING_REGULAR),
  ) {
    Surface(
        modifier = Modifier.size(SURFACE_BIG),
        // TODO to modify when we will have our app logo
        shape = CircleShape,
        color = lightOrange,
    ) {
      Box(contentAlignment = Alignment.Center) {
        AuthenticationText(
            text = "logo",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
      }
    }

    AuthenticationText(
        text = "Amber",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
  }
}

/**
 * Displays a text input field styled consistently across authentication screens.
 *
 * The field supports different keyboard configurations and visual transformations so that it can be
 * reused for both email and password input.
 *
 * @param value Current value displayed in the input field.
 * @param onValueChange Called whenever the field value changes.
 * @param placeholder Text displayed while the field is empty.
 * @param keyboardOptions Keyboard configuration appropriate for the expected input.
 * @param modifier Modifier applied to the text field.
 * @param visualTransformation Transformation applied when displaying the text, such as hiding
 *   password characters.
 */
@Composable
internal fun AuthenticationTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
  OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      modifier = modifier.fillMaxWidth().height(PADDING_ENORMOUS),
      singleLine = true,
      placeholder = {
        AuthenticationText(
            text = placeholder,
            style = MaterialTheme.typography.bodySmall,
            color = darkGray,
        )
      },
      keyboardOptions = keyboardOptions,
      visualTransformation = visualTransformation,
      shape = RoundedCornerShape(SMALL_ROUND_CORNER),
      colors =
          OutlinedTextFieldDefaults.colors(
              unfocusedBorderColor = lightGray,
              focusedBorderColor = darkGray,
              cursorColor = darkGray,
          ),
  )
}

/**
 * Displays the common primary button used throughout the authentication flow.
 *
 * @param text Text displayed inside the button.
 * @param onClick Called when the user presses the button.
 * @param modifier Modifier applied to the button.
 */
@Composable
internal fun AuthenticationButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Button(
      onClick = onClick,
      modifier = modifier.fillMaxWidth().height(PADDING_ENORMOUS),
      shape = RoundedCornerShape(SMALL_ROUND_CORNER),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = lightOrange,
              contentColor = Color.Black,
          ),
  ) {
    AuthenticationText(
        text = text,
        style = MaterialTheme.typography.labelLarge,
    )
  }
}

/**
 * Displays text used by the authentication screens with optional presentation overrides.
 *
 * When [color] is unspecified, the text uses the style's color or the surrounding content color.
 *
 * @param modifier Modifier applied to the text.
 * @param text Content to display.
 * @param textAlign Alignment of lines within the text when it wraps.
 * @param style Typography style applied to the text.
 * @param fontWeight Optional weight overriding the style's weight.
 * @param color Optional text color; defaults to [Color.Unspecified].
 */
@Composable
internal fun AuthenticationText(
    modifier: Modifier = Modifier,
    text: String,
    textAlign: TextAlign? = null,
    style: TextStyle,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
) {
  Text(
      text = text,
      textAlign = textAlign,
      modifier = modifier,
      style = style,
      fontWeight = fontWeight,
      color = color,
  )
}

/**
 * Displays the terms-of-service and privacy-policy notice shared by authentication screens.
 *
 * @param modifier Modifier applied to the footer container.
 */
@Composable
internal fun TermsFooter(
    modifier: Modifier = Modifier,
) {
  Column(
      modifier = modifier.testTag(AuthenticationComponentTestTags.TERMS_FOOTER),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    AuthenticationText(
        text = "By clicking continue, you agree to our Terms of Service and Privacy Policy",
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = darkGray,
    )
  }
}
