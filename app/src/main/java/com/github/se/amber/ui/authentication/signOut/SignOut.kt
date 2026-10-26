// Written by Alaabrine, with assistance from Cursor.
package com.github.se.amber.ui.authentication.signOut

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_BIG
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL

/** Test tags of [SignOutScreen]. */
object SignOutTestTags {
  const val TITLE = "SignOut_test_tag_title"
  const val EXPLANATION = "SignOut_test_tag_explanation"
}

/**
 * Confirmation screen shared by caregiver and patient parameters.
 *
 * Shows a short explanation and the shared [SignOutButton] helper. Navigation after a successful
 * sign-out is handled by [onSignedOut].
 *
 * @param viewModel ViewModel shared with [SignOutButton].
 * @param onSignedOut Called once sign-out succeeds.
 * @param onBack Called when the user cancels from the top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignOutScreen(
    viewModel: SignOutViewModel = viewModel(),
    onSignedOut: () -> Unit = {},
    onBack: () -> Unit = {},
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(uiState.errorMsg) {
    uiState.errorMsg?.let { message ->
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
      viewModel.clearErrorMsg()
    }
  }

  LaunchedEffect(uiState.signedOut) {
    if (uiState.signedOut) {
      onSignedOut()
      Toast.makeText(context, "Logout successful", Toast.LENGTH_SHORT).show()
    }
  }

  Scaffold(
      topBar = {
        Column(modifier = Modifier.fillMaxWidth().padding(CAREGIVER_PADDING_SMALL)) {
          TopNavigationMenu(
              action = TopNavigationAction.CANCEL,
              onBackClick = onBack,
          )
          Text(
              text = "Sign out",
              color = MaterialTheme.colorScheme.primary,
              style = MaterialTheme.typography.headlineSmall,
              modifier =
                  Modifier.fillMaxWidth()
                      .padding(top = CAREGIVER_PADDING_SMALL, bottom = CAREGIVER_PADDING_MEDIUM)
                      .testTag(SignOutTestTags.TITLE),
              textAlign = TextAlign.Center,
          )
        }
      },
      content = { paddingValues ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = CAREGIVER_PADDING_BIG),
            verticalArrangement =
                Arrangement.spacedBy(CAREGIVER_PADDING_BIG, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
              text =
                  "You will be signed out of Amber on this device. You can sign in again at any time.",
              color = MaterialTheme.colorScheme.onBackground,
              style = MaterialTheme.typography.bodyLarge,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth().testTag(SignOutTestTags.EXPLANATION),
          )
          SignOutButton(viewModel = viewModel)
        }
      },
  )
}

/** Preview of [SignOutScreen] using the Amber application theme. */
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SignOutScreenPreview() {
  AmberAppTheme(dynamicColor = false) { SignOutScreen() }
}
