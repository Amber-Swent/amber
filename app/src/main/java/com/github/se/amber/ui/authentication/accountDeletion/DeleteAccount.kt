// Written by MeiliV, with assistance from OpenAI Codex.
// Modified by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.authentication.accountDeletion

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.ui.caregiver.DisplayCaregiverButton
import com.github.se.amber.ui.caregiver.DisplayCaregiverTitle
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.patient.DisplayPatientButton
import com.github.se.amber.ui.patient.DisplayPatientTitle
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_BIG
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.theme.PATIENT_PADDING_MEDIUM
import com.github.se.amber.ui.theme.WEIGHT_FIFTEENTH
import com.github.se.amber.ui.utils.asString

/** Tags for the title, explanation, and confirmation button. */
object DeleteAccountTestTags {
  const val TITLE = "delete_account_title"
  const val EXPLANATION = "delete_account_explanation"
  const val BUTTON = "delete_account_button"
}

/**
 * Confirms permanent deletion of a caregiver account.
 *
 * Family consequences are loaded before the button appears. During deletion the screen shows
 * progress and prevents leaving through Cancel or system Back. The repository restarts the app on
 * success, so this screen does not navigate after confirmation.
 *
 * The screen is shared by both roles: [isPatient] selects the patient or caregiver title, buttons,
 * padding and text size. Patient screens use larger text.
 *
 * @param isPatient Boolean indicating if the user viewing the screen is a Patient.
 * @param viewModel Owns the warnings, reauthentication, deletion, and retry state.
 * @param credentialManager Requests a fresh Google credential when deletion is confirmed.
 * @param onBack Returns to parameters when deletion is not running.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteAccountScreen(
    isPatient: Boolean = true,
    viewModel: DeleteAccountViewModel = viewModel(),
    credentialManager: CredentialManager = CredentialManager.create(LocalContext.current),
    onBack: () -> Unit = {},
) {
  val context = LocalContext.current
  val resources = LocalResources.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val textStyle = deleteAccountTextStyle(isPatient)
  val padding = if (isPatient) PATIENT_PADDING_MEDIUM else CAREGIVER_PADDING_BIG

  // Consume system Back while the deletion request is in progress.
  BackHandler(enabled = uiState.isDeleting) {}
  LaunchedEffect(Unit) { viewModel.loadWarnings() }

  LaunchedEffect(uiState.errorMessageRes) {
    uiState.errorMessageRes?.let { messageRes ->
      Toast.makeText(context, resources.getString(messageRes), Toast.LENGTH_LONG).show()
      viewModel.clearError()
    }
  }
  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Column(modifier = Modifier.fillMaxWidth().padding(CAREGIVER_PADDING_SMALL)) {
                if (!uiState.isDeleting) {
                  TopNavigationMenu(
                      action = TopNavigationAction.CANCEL,
                      onBackClick = onBack,
                  )
                }
                if (isPatient) {
                  DisplayPatientTitle(
                      title = stringResource(R.string.deleteAccountTitle),
                      testTag = DeleteAccountTestTags.TITLE,
                  )
                } else {
                  DisplayCaregiverTitle(
                      modifier = Modifier.testTag(DeleteAccountTestTags.TITLE),
                      title = stringResource(R.string.deleteAccountTitle),
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
                      .padding(
                          start = padding,
                          end = padding,
                          bottom = maxHeight * WEIGHT_FIFTEENTH,
                      ),
              verticalArrangement = Arrangement.spacedBy(padding, Alignment.CenterVertically),
              horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
                text =
                    stringResource(
                        if (isPatient) R.string.deleteAccountPatientExplanation
                        else R.string.deleteAccountExplanation
                    ),
                color = MaterialTheme.colorScheme.onBackground,
                style = textStyle,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag(DeleteAccountTestTags.EXPLANATION),
            )
            uiState.warnings.forEach { warning ->
              Text(
                  text = warning.asString(resources),
                  color = MaterialTheme.colorScheme.error,
                  style = textStyle,
                  textAlign = TextAlign.Center,
              )
            }
            when {
              uiState.isDeleting -> {
                Text(
                    text = stringResource(R.string.deleteAccountProgress),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = textStyle,
                    textAlign = TextAlign.Center,
                )
              }
              uiState.warningsLoaded -> {
                DeleteAccountButton(
                    isPatient = isPatient,
                    text =
                        if (uiState.localCleanupNeeded)
                            stringResource(R.string.deleteAccountCleanupLabel)
                        else stringResource(R.string.deleteAccountTitle),
                    onClick = { viewModel.deleteAccount(context, credentialManager) },
                    testTag = DeleteAccountTestTags.BUTTON,
                )
              }
              uiState.isLoading -> {
                Text(
                    text = stringResource(R.string.deleteAccountCheckingLabel),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = textStyle,
                )
              }
              else -> {
                DeleteAccountButton(
                    isPatient = isPatient,
                    text = stringResource(R.string.deleteAccountRetryLabel),
                    onClick = viewModel::loadWarnings,
                )
              }
            }
          }
        }
      },
  )
}

/** Text style of the screen body: patient screens need larger text than caregiver ones. */
@Composable
private fun deleteAccountTextStyle(isPatient: Boolean): TextStyle =
    if (isPatient) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge

/**
 * Button of the screen, built with the shared helper matching role - caregiver or patient.
 *
 * @param isPatient Boolean indicating if the user viewing the screen is a Patient.
 * @param text Label of the button.
 * @param onClick Callback invoked when the button is clicked.
 * @param testTag Optional test tag. The helper's default tag is kept when null.
 */
@Composable
private fun DeleteAccountButton(
    isPatient: Boolean = true,
    text: String = "",
    onClick: () -> Unit = {},
    testTag: String? = null,
) {
  if (isPatient) {
    DisplayPatientButton(text = text, onClick = onClick, testTag = testTag)
  } else if (testTag != null) {
    DisplayCaregiverButton(text = text, onClick = onClick, buttonTestTag = testTag)
  } else {
    DisplayCaregiverButton(text = text, onClick = onClick)
  }
}
