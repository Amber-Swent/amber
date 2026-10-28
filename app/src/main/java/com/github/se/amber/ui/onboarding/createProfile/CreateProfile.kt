// this code was written by Alaabrine
// with the aid of AI coding agent
// Modified by Yuhan YANG, with assistance from Cursor.
package com.github.se.amber.ui.onboarding.createProfile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.ui.onboarding.AmberHeader
import com.github.se.amber.ui.patient.DisplayPatientButton
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_BIG
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.theme.ROUND_CORNER

/** Stable tags for locating the form controls and validation messages in UI tests. */
object CreateProfileTestTags {
  const val NAME_INPUT = "CreateProfile_test_tag_name_input"
  const val SURNAME_INPUT = "CreateProfile_test_tag_surname_input"
  const val NAME_ERROR = "CreateProfile_test_tag_name_error"
  const val SURNAME_ERROR = "CreateProfile_test_tag_surname_error"
  const val CAREGIVER_BUTTON = "CreateProfile_test_tag_caregiver_button"
  const val SUBMIT_ERROR = "CreateProfile_test_tag_submit_error"
}

/**
 * First onboarding screen after sign-in: the user creates their Amber profile.
 *
 * Amber users are either a Patient or a Caregiver. This screen collects a first and last name, then
 * persists the profile when the user taps one of those role buttons. Validation and the repository
 * write live in [CreateProfileViewModel]; this composable only renders [viewModel] state and
 * forwards user actions. [onProfileCreated] should navigate to the next onboarding step.
 *
 * @param modifier Modifier applied to the root profile layout.
 * @param onProfileCreated called with the saved [Role] after a successful create.
 * @param viewModel UI state and events for this screen. Defaults to a ViewModel scoped to the
 *   composition; tests can pass a custom instance with a fake [ProfileRepository].
 */
@Composable
fun CreateProfileScreen(
    modifier: Modifier = Modifier,
    onProfileCreated: (Role) -> Unit = {},
    viewModel: CreateProfileViewModel = viewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  Surface(
      modifier = modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background,
      contentColor = MaterialTheme.colorScheme.onBackground,
  ) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CAREGIVER_PADDING_BIG, vertical = CAREGIVER_PADDING_BIG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_BIG),
    ) {
      AmberHeader()

      Text(
          text = "Create an account",
          modifier = Modifier.fillMaxWidth().padding(top = CAREGIVER_PADDING_MEDIUM),
          style = MaterialTheme.typography.headlineMedium,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.primary,
      )
      Text(
          text = "What should we call you?",
          modifier = Modifier.fillMaxWidth(),
          style = MaterialTheme.typography.titleMedium,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.secondary,
      )

      ProfileNameField(
          value = uiState.name,
          onValueChange = viewModel::onNameChange,
          label = "Name",
          fieldTag = CreateProfileTestTags.NAME_INPUT,
          error = uiState.nameError,
          errorTag = CreateProfileTestTags.NAME_ERROR,
      )
      ProfileNameField(
          value = uiState.surname,
          onValueChange = viewModel::onSurnameChange,
          label = "Surname",
          fieldTag = CreateProfileTestTags.SURNAME_INPUT,
          error = uiState.surnameError,
          errorTag = CreateProfileTestTags.SURNAME_ERROR,
      )

      Text(
          text = "Please select your role",
          modifier = Modifier.fillMaxWidth().padding(top = CAREGIVER_PADDING_MEDIUM),
          style = MaterialTheme.typography.titleMedium,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.secondary,
      )

      DisplayPatientButton(
          text = "Patient",
          enabled = !uiState.isSaving,
          onClick = { viewModel.onRoleSelected(Role.PATIENT, onProfileCreated) },
      )
      DisplayPatientButton(
          text = "Caregiver",
          enabled = !uiState.isSaving,
          testTag = CreateProfileTestTags.CAREGIVER_BUTTON,
          onClick = { viewModel.onRoleSelected(Role.CAREGIVER, onProfileCreated) },
      )

      uiState.submitError?.let { errorMessage ->
        Text(
            text = errorMessage,
            modifier =
                Modifier.fillMaxWidth()
                    .padding(top = CAREGIVER_PADDING_SMALL)
                    .testTag(CreateProfileTestTags.SUBMIT_ERROR),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

/**
 * Name field used by [CreateProfileScreen].
 *
 * @param value current text.
 * @param onValueChange called when the text changes.
 * @param label label and placeholder shown for the field.
 * @param fieldTag test tag of the field.
 * @param error supporting error text, or null when the field is valid.
 * @param errorTag test tag of the supporting error text.
 */
@Composable
private fun ProfileNameField(
    value: String = "",
    onValueChange: (String) -> Unit = {},
    label: String = "",
    fieldTag: String = "",
    error: String? = null,
    errorTag: String = "",
) {
  OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      modifier = Modifier.fillMaxWidth().testTag(fieldTag),
      label = { Text(text = label, style = MaterialTheme.typography.labelMedium) },
      placeholder = { Text(text = label, style = MaterialTheme.typography.labelMedium) },
      supportingText =
          error?.let { message ->
            {
              Text(
                  text = message,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error,
                  modifier = Modifier.testTag(errorTag),
              )
            }
          },
      isError = error != null,
      singleLine = true,
      shape = RoundedCornerShape(ROUND_CORNER),
      colors =
          OutlinedTextFieldDefaults.colors(
              focusedBorderColor = MaterialTheme.colorScheme.outline,
              unfocusedBorderColor = MaterialTheme.colorScheme.outline,
              focusedLabelColor = MaterialTheme.colorScheme.onSurface,
              unfocusedLabelColor = MaterialTheme.colorScheme.onSurface,
              cursorColor = MaterialTheme.colorScheme.onSurface,
              focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface,
              unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface,
              errorBorderColor = MaterialTheme.colorScheme.error,
              errorLabelColor = MaterialTheme.colorScheme.error,
              errorCursorColor = MaterialTheme.colorScheme.error,
              errorSupportingTextColor = MaterialTheme.colorScheme.error,
          ),
  )
}

@Preview(showBackground = true)
@Composable
fun CreateProfilePreview() {
  val fakeRepository =
      object : ProfileRepository {
        override suspend fun createProfile(person: Person, role: Role) = Result.success(Unit)

        override suspend fun getCurrentProfile() = Result.success(null)

        override suspend fun getProfile(uid: String) = Result.success(null)
      }
  val viewModel = remember { CreateProfileViewModel(profileRepository = fakeRepository) }
  AmberAppTheme { CreateProfileScreen(viewModel = viewModel) }
}
