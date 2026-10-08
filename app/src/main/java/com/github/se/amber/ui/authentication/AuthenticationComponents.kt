// This file was written with the aid of AI
package com.github.se.amber.ui.authentication

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.github.se.amber.ui.theme.darkGray
import com.github.se.amber.ui.theme.lightGray
import com.github.se.amber.ui.theme.lightOrange

private val fieldShape = RoundedCornerShape(8.dp) // Only used inside this file
internal val buttonShape = RoundedCornerShape(7.dp)

/**
 * Displays the shared password authentication UI used by patient and caregiver screens.
 *
 * The screen shows the selected email address, a password field, a Continue button, and a
 * forgotten-password action. Role-specific behavior is represented through
 * [forgotPasswordExplanation].
 *
 * This function is made internal so that it is accessible from the Amber module.
 *
 * @param email Email address displayed to the user.
 * @param password Current value of the password input field.
 * @param onPasswordChange Called whenever the user modifies the password input.
 * @param onContinue Called when the user presses the Continue button.
 * @param onForgotPassword Called when the user presses the forgotten-password button.
 * @param forgotPasswordExplanation Explanation displayed below the forgotten-password button.
 * @param modifier Modifier applied to the root authentication layout.
 */
@Composable
internal fun PasswordScreen(
    email: String,
    password: String,
    onPasswordChange: (String) -> Unit,
    onContinue: () -> Unit,
    onForgotPassword: () -> Unit,
    forgotPasswordExplanation: String,
    modifier: Modifier = Modifier,
) {
  AuthenticationLayout(modifier = modifier) {
    Text(
        text = "Your are logged in with",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )

    Text(
        text = "<$email>",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )

    Spacer(modifier = Modifier.height(4.dp))

    Text(
        text = "Enter your password to sign up",
        style = MaterialTheme.typography.bodySmall,
    )

    Spacer(modifier = Modifier.height(12.dp))

    AuthenticationTextField(
        value = password,
        onValueChange = onPasswordChange,
        placeholder = "Enter your password",
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
    )

    Spacer(modifier = Modifier.height(12.dp))

    AuthenticationButton(
        text = "Continue",
        onClick = onContinue,
    )

    Spacer(modifier = Modifier.height(46.dp))

    AuthenticationButton(
        text = "Password Forgotten ?",
        onClick = onForgotPassword,
    )

    Spacer(modifier = Modifier.height(10.dp))

    Text(
        text = forgotPasswordExplanation,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth(),
    )
  }
}

/**
 * Provides the common layout shared by all authentication screens.
 *
 * The layout displays the Amber authentication header at the top, screen-specific [content] in the
 * center, and the terms and privacy notice at the bottom.
 *
 * @param modifier Modifier applied to the root layout.
 * @param content Screen-specific composable content displayed in the center of the layout.
 */
@Composable
internal fun AuthenticationLayout(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
  // Surface will be the background/container for the screen
  Surface(
      modifier = modifier.fillMaxSize(),
      color = Color.White,
  ) {
    // Use Box, a Column would stack every items sequentially
    Box(
        modifier =
            Modifier.fillMaxSize()
                .statusBarsPadding() // Avoids drawing underneath Android's top system bar
                .navigationBarsPadding() // Avoid drawing underneath navigation area at the bottom
                .padding(horizontal = 22.dp),
    ) {
      AuthenticationHeader(
          modifier = Modifier.align(Alignment.TopCenter).padding(top = 42.dp),
      )

      Column(
          modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          content = { content() },
      )

      TermsFooter(
          modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
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
      modifier = modifier,
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Surface(
        modifier = Modifier.size(84.dp),
        // TODO to modify when we will have our app logo
        shape = CircleShape,
        color = lightOrange,
    ) {
      Box(contentAlignment = Alignment.Center) {
        Text(
            text = "logo",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    Text(
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
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
) {
  OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      modifier = modifier.fillMaxWidth().height(54.dp),
      singleLine = true,
      placeholder = {
        Text(
            text = placeholder,
            style = MaterialTheme.typography.bodySmall,
            color = darkGray,
        )
      },
      keyboardOptions = keyboardOptions,
      visualTransformation = visualTransformation,
      shape = fieldShape,
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
      modifier = modifier.fillMaxWidth().height(44.dp),
      shape = buttonShape,
      colors =
          ButtonDefaults.buttonColors(
              containerColor = lightOrange,
              contentColor = Color.Black,
          ),
  ) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
    )
  }
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
      modifier = modifier,
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
        text = "By clicking continue, you agree to our Terms of Service",
        style = MaterialTheme.typography.bodySmall,
        color = darkGray,
    )

    Text(
        text = "and Privacy Policy",
        style = MaterialTheme.typography.bodySmall,
        color = darkGray,
    )
  }
}
