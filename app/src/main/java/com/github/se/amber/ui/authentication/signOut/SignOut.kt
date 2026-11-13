// Written by Alaabrine, with assistance from Cursor.
// Modified by Alaabrine, with assistance from Cursor.
// Modified by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.authentication.signOut

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.ui.caregiver.DisplayCaregiverTitle
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.patient.DisplayPatientTitle
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_BIG
import com.github.se.amber.ui.theme.CAREGIVER_BUTTON_HEIGHT_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_BUTTON_WIDTH_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.theme.PATIENT_BUTTON_HEIGHT_MEDIUM
import com.github.se.amber.ui.theme.PATIENT_BUTTON_WIDTH
import com.github.se.amber.ui.theme.PATIENT_PADDING_MEDIUM

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
 * The screen is shared by both roles: [isPatient] selects the patient or caregiver title, padding,
 * text size and button size. Patient screens use larger elements.
 *
 * @param isPatient Boolean indicating if the user viewing the screen is a Patient.
 * @param viewModel ViewModel shared with [SignOutButton].
 * @param onSignedOut Called once sign-out succeeds.
 * @param onBack Called when the user cancels from the top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignOutScreen(
    isPatient: Boolean = true,
    viewModel: SignOutViewModel = viewModel(),
    onSignedOut: () -> Unit = {},
    onBack: () -> Unit = {},
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val textStyle = signOutTextStyle(isPatient)
  val buttonTextStyle =
      if (isPatient) MaterialTheme.typography.headlineMedium
      else MaterialTheme.typography.labelLarge
  val padding = if (isPatient) PATIENT_PADDING_MEDIUM else CAREGIVER_PADDING_BIG
  // Configuration-aware resources, so the toast follows the app language
  val resources = LocalResources.current

  LaunchedEffect(uiState.errorMsgRes) {
    uiState.errorMsgRes?.let { errorMsgRes ->
      Toast.makeText(context, resources.getString(errorMsgRes), Toast.LENGTH_SHORT).show()
      viewModel.clearErrorMsg()
    }
  }

  LaunchedEffect(uiState.signedOut) {
    if (uiState.signedOut) {
      onSignedOut()
      Toast.makeText(context, resources.getString(R.string.signOutSuccess), Toast.LENGTH_SHORT)
          .show()
    }
  }

  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Column(modifier = Modifier.fillMaxWidth().padding(CAREGIVER_PADDING_SMALL)) {
                TopNavigationMenu(
                    action = TopNavigationAction.CANCEL,
                    onBackClick = onBack,
                )
                if (isPatient) {
                  DisplayPatientTitle(
                      title = stringResource(R.string.signOutTitle),
                      testTag = SignOutTestTags.TITLE,
                  )
                } else {
                  DisplayCaregiverTitle(
                      modifier = Modifier.testTag(SignOutTestTags.TITLE),
                      title = stringResource(R.string.signOutTitle),
                  )
                }
              }
            }
        )
      },
      content = { paddingValues ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
          Column(
              modifier =
                  Modifier.fillMaxWidth()
                      .verticalScroll(rememberScrollState())
                      .heightIn(min = maxHeight)
                      .padding(horizontal = padding),
              verticalArrangement =
                  Arrangement.spacedBy(padding, Alignment.CenterVertically),
              horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
                text = stringResource(R.string.signOutExplanation),
                color = MaterialTheme.colorScheme.onBackground,
                style = textStyle,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag(SignOutTestTags.EXPLANATION),
            )
            SignOutButton(
                viewModel = viewModel,
                buttonHeight =
                    if (isPatient) PATIENT_BUTTON_HEIGHT_MEDIUM else CAREGIVER_BUTTON_HEIGHT_MEDIUM,
                buttonWidth = if (isPatient) PATIENT_BUTTON_WIDTH else CAREGIVER_BUTTON_WIDTH_MEDIUM,
                textStyle = buttonTextStyle,
            )
          }
        }
      },
  )
}

/** Text style of the screen body: patient screens need larger text than caregiver ones. */
@Composable
private fun signOutTextStyle(isPatient: Boolean): TextStyle =
    if (isPatient) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge

/** Preview of [SignOutScreen] using the Amber application theme. */
@Preview(showBackground = true, showSystemUi = true)
@Composable
internal fun SignOutScreenPreview() {
  AmberAppTheme(dynamicColor = false) {
    // Omit onSignedOut/onBack so their default branches are exercised by preview/tests.
    // Reuses the preview ViewModel from SignOutUtils (constructed outside a composable).
    SignOutScreen(viewModel = previewSignOutViewModel)
  }
}
