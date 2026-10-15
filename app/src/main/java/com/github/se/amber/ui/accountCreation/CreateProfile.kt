// this code was written by Alaabrine
// with the aid of AI coding agent
package com.github.se.amber.ui.accountCreation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.model.user.Role
import com.github.se.amber.ui.theme.PADDING_BIG
import com.github.se.amber.ui.theme.PADDING_MEDIUM
import com.github.se.amber.ui.theme.PADDING_SMALL
import com.github.se.amber.ui.theme.darkGray
import com.github.se.amber.ui.theme.lightOrange

/** Test tags for [CreateProfileScreen]. */
enum class CreateProfileTestTags {
  NAME_INPUT,
  SURNAME_INPUT,
  NAME_PLACEHOLDER,
  SURNAME_PLACEHOLDER,
  NAME_ERROR,
  SURNAME_ERROR,
  PATIENT_BUTTON,
  CAREGIVER_BUTTON,
  SUBMIT_ERROR,
}

/** Grey color used for empty Name / Surname labels. */
val CreateProfilePlaceholderColor: Color = darkGray

private const val FILL_OUT_MESSAGE = "Please fill this out"

/**
 * First onboarding screen after sign-in: the user creates their Amber profile.
 *
 * Amber users are either a Patient or a Caregiver. This screen collects a first and last name,
 * then persists the profile when the user taps one of those role buttons. Validation and the
 * repository write live in [CreateProfileViewModel]; this composable only renders [viewModel]
 * state and forwards user actions.
 *
 * @param viewModel UI state and events for this screen. Defaults to a ViewModel scoped to the
 *   composition; tests can pass a custom instance with a fake [com.github.se.amber.data.profile.ProfileRepository].
 */
@Composable
fun CreateProfileScreen(viewModel: CreateProfileViewModel = viewModel()) {
  val uiState by viewModel.uiState.collectAsState()

  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(Color.White)
              .padding(horizontal = PADDING_BIG, vertical = PADDING_MEDIUM),
      verticalArrangement = Arrangement.spacedBy(PADDING_BIG + PADDING_MEDIUM),
  ) {
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(PADDING_SMALL),
    ) {
      Box(
          modifier = Modifier.size(PADDING_BIG * 2).background(lightOrange, CircleShape),
          contentAlignment = Alignment.Center,
      ) {
        Text(
            text = "logo",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Black,
        )
      }
      Text(
          text = "Amber",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
      )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PADDING_BIG),
    ) {
      Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(PADDING_SMALL),
      ) {
        Text(
            text = "Create an account",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
        )
        Text(
            text = "What should we call you?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
        )
      }

      Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(PADDING_MEDIUM),
      ) {
        ProfileTextField(
            value = uiState.name,
            onValueChange = viewModel::onNameChange,
            label = "Name",
            inputTestTag = CreateProfileTestTags.NAME_INPUT.name,
            labelTestTag = CreateProfileTestTags.NAME_PLACEHOLDER.name,
            errorTestTag = CreateProfileTestTags.NAME_ERROR.name,
            isError = uiState.nameError,
        )

        ProfileTextField(
            value = uiState.surname,
            onValueChange = viewModel::onSurnameChange,
            label = "Surname",
            inputTestTag = CreateProfileTestTags.SURNAME_INPUT.name,
            labelTestTag = CreateProfileTestTags.SURNAME_PLACEHOLDER.name,
            errorTestTag = CreateProfileTestTags.SURNAME_ERROR.name,
            isError = uiState.surnameError,
        )
      }

      Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(PADDING_MEDIUM),
      ) {
        Text(
            text = "Please select your role",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PADDING_MEDIUM),
        ) {
          RoleChoiceButton(
              modifier = Modifier.weight(1f),
              role = Role.PATIENT,
              label = "Patient",
              testTag = CreateProfileTestTags.PATIENT_BUTTON,
              enabled = !uiState.isSaving,
              onRoleSelected = viewModel::onRoleSelected,
          )
          RoleChoiceButton(
              modifier = Modifier.weight(1f),
              role = Role.CAREGIVER,
              label = "Caregiver",
              testTag = CreateProfileTestTags.CAREGIVER_BUTTON,
              enabled = !uiState.isSaving,
              onRoleSelected = viewModel::onRoleSelected,
          )
        }

        uiState.submitError?.let { errorMessage ->
          Text(
              text = errorMessage,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.testTag(CreateProfileTestTags.SUBMIT_ERROR.name),
          )
        }
      }
    }
  }
}

/**
 * Role selection button for Patient or Caregiver.
 *
 * @param modifier layout modifier applied to the button.
 * @param role role submitted when the button is pressed.
 * @param label text shown on the button.
 * @param testTag tag used by UI tests to find this button.
 * @param enabled whether the button accepts clicks.
 * @param onRoleSelected callback invoked with [role] on click.
 */
@Composable
private fun RoleChoiceButton(
    modifier: Modifier = Modifier,
    role: Role,
    label: String,
    testTag: CreateProfileTestTags,
    enabled: Boolean,
    onRoleSelected: (Role) -> Unit,
) {
  Button(
      onClick = { onRoleSelected(role) },
      enabled = enabled,
      modifier = modifier.height(PADDING_BIG * 2).testTag(testTag.name),
      shape = RoundedCornerShape(PADDING_SMALL),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = lightOrange,
              contentColor = Color.Black,
          ),
  ) {
    Text(text = label, fontWeight = FontWeight.Medium)
  }
}

/**
 * Single name or surname input used on [CreateProfileScreen].
 *
 * Renders an [OutlinedTextField] with a floating Material [label]. When [isError] is true, shows
 * a "Please fill this out" message below the field. The error [Text] stays outside the text field
 * so UI tests can target it with [errorTestTag] without using an unmerged semantics tree; the
 * label node is tagged with [labelTestTag] and must be queried with `useUnmergedTree = true`.
 *
 * @param value current text bound to the field (name or surname from the ViewModel).
 * @param onValueChange invoked on every edit; typically [CreateProfileViewModel.onNameChange] or
 *   [CreateProfileViewModel.onSurnameChange].
 * @param label floating label text (e.g. "Name" or "Surname").
 * @param inputTestTag [CreateProfileTestTags] name applied to the text field itself.
 * @param labelTestTag [CreateProfileTestTags] name applied to the label [Text] inside the field.
 * @param errorTestTag [CreateProfileTestTags] name applied to the fill-out error [Text].
 * @param isError when true, marks the field as errored and shows the fill-out message.
 */
@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    inputTestTag: String,
    labelTestTag: String,
    errorTestTag: String,
    isError: Boolean,
) {
  Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(PADDING_SMALL),
  ) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().testTag(inputTestTag),
        label = {
          Text(
              text = label,
              modifier = Modifier.testTag(labelTestTag),
          )
        },
        isError = isError,
        singleLine = true,
        shape = RoundedCornerShape(PADDING_SMALL),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = darkGray,
                unfocusedBorderColor = darkGray.copy(alpha = 0.4f),
                focusedLabelColor = darkGray,
                unfocusedLabelColor = CreateProfilePlaceholderColor,
                errorLabelColor = MaterialTheme.colorScheme.error,
                errorBorderColor = MaterialTheme.colorScheme.error,
            ),
    )
    if (isError) {
      Text(
          text = FILL_OUT_MESSAGE,
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.padding(start = PADDING_MEDIUM).testTag(errorTestTag),
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
fun CreateProfilePreview(viewModel: CreateProfileViewModel = CreateProfileViewModel()) {
  CreateProfileScreen(viewModel = viewModel)
}
