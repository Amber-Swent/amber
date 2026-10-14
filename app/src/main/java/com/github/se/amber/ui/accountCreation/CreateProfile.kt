// this code was written by Alaabrine
// with the aid of AI coding agent
package com.github.se.amber.ui.accountCreation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.model.user.Role
import com.github.se.amber.ui.theme.PADDING_BIG
import com.github.se.amber.ui.theme.PADDING_MEDIUM
import com.github.se.amber.ui.theme.PADDING_SMALL
import com.github.se.amber.ui.theme.darkGray
import com.github.se.amber.ui.theme.lightOrange

/** Test tags for [CreateProfile]. */
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

/** Grey color used for empty Name / Surname placeholders. */
val CreateProfilePlaceholderColor: Color = darkGray

private const val FILL_OUT_MESSAGE = "Please fill this out"

private val ErrorRed = Color(0xFFB3261E)

/**
 * Create-profile step of account creation.
 *
 * Collects the user's name and surname, then lets them choose Patient or Caregiver. On successful
 * profile creation, [onProfileCreated] is invoked with the persisted [Role] and the display name
 * (first name) so a navigation owner can route patients to
 * [com.github.se.amber.ui.navigation.OnboardingScreen.EnterInvitation] and caregivers to
 * [com.github.se.amber.ui.navigation.OnboardingScreen.CaregiverChoice].
 */
@Composable
fun CreateProfile(
    viewModel: CreateProfileViewModel = viewModel(),
    onProfileCreated: (role: Role, displayName: String) -> Unit = { _, _ -> },
) {
  val uiState by viewModel.uiState.collectAsState()

  LaunchedEffect(uiState.createdRole) {
    val role = uiState.createdRole ?: return@LaunchedEffect
    onProfileCreated(role, uiState.name)
    viewModel.onCreatedRoleHandled()
  }

  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(Color.White)
              .padding(horizontal = PADDING_BIG, vertical = PADDING_MEDIUM),
  ) {
    Column(horizontalAlignment = Alignment.Start) {
      Box(
          modifier = Modifier.size(56.dp).background(lightOrange, CircleShape),
          contentAlignment = Alignment.Center,
      ) {
        Text(text = "logo", fontSize = 12.sp, color = Color.Black)
      }
      Text(
          text = "Amber",
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          modifier = Modifier.padding(top = PADDING_SMALL),
      )
    }

    Spacer(modifier = Modifier.height(40.dp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
          text = "Create an account",
          fontWeight = FontWeight.Bold,
          fontSize = 28.sp,
          color = Color.Black,
      )
      Spacer(modifier = Modifier.height(PADDING_SMALL))
      Text(
          text = "What should we call you?",
          fontWeight = FontWeight.Bold,
          fontSize = 16.sp,
          color = Color.Black,
      )

      Spacer(modifier = Modifier.height(PADDING_BIG))

      ProfileTextField(
          value = uiState.name,
          onValueChange = viewModel::onNameChange,
          placeholder = "Name",
          inputTestTag = CreateProfileTestTags.NAME_INPUT.name,
          placeholderTestTag = CreateProfileTestTags.NAME_PLACEHOLDER.name,
          errorTestTag = CreateProfileTestTags.NAME_ERROR.name,
          isError = uiState.nameError,
      )

      Spacer(modifier = Modifier.height(PADDING_MEDIUM))

      ProfileTextField(
          value = uiState.surname,
          onValueChange = viewModel::onSurnameChange,
          placeholder = "Surname",
          inputTestTag = CreateProfileTestTags.SURNAME_INPUT.name,
          placeholderTestTag = CreateProfileTestTags.SURNAME_PLACEHOLDER.name,
          errorTestTag = CreateProfileTestTags.SURNAME_ERROR.name,
          isError = uiState.surnameError,
      )

      Spacer(modifier = Modifier.height(PADDING_BIG))

      Text(
          text = "Please select your role",
          fontWeight = FontWeight.Bold,
          fontSize = 16.sp,
          color = Color.Black,
      )

      Spacer(modifier = Modifier.height(PADDING_MEDIUM))

      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(PADDING_MEDIUM),
      ) {
        Button(
            onClick = { viewModel.onRoleSelected(Role.PATIENT) },
            enabled = !uiState.isSaving,
            modifier =
                Modifier.weight(1f)
                    .height(48.dp)
                    .testTag(CreateProfileTestTags.PATIENT_BUTTON.name),
            shape = RoundedCornerShape(8.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = lightOrange,
                    contentColor = Color.Black,
                ),
        ) {
          Text(text = "Patient", fontWeight = FontWeight.Medium)
        }

        Button(
            onClick = { viewModel.onRoleSelected(Role.CAREGIVER) },
            enabled = !uiState.isSaving,
            modifier =
                Modifier.weight(1f)
                    .height(48.dp)
                    .testTag(CreateProfileTestTags.CAREGIVER_BUTTON.name),
            shape = RoundedCornerShape(8.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = lightOrange,
                    contentColor = Color.Black,
                ),
        ) {
          Text(text = "Caregiver", fontWeight = FontWeight.Medium)
        }
      }

      uiState.submitError?.let { errorMessage ->
        Text(
            text = errorMessage,
            color = ErrorRed,
            fontSize = 12.sp,
            modifier =
                Modifier.padding(top = PADDING_SMALL)
                    .testTag(CreateProfileTestTags.SUBMIT_ERROR.name),
        )
      }
    }
  }
}

/**
 * Name/surname field with externally tagged placeholder and error text so UI tests can find them in
 * the merged semantics tree (outside [OutlinedTextField]'s merged descendants).
 */
@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    inputTestTag: String,
    placeholderTestTag: String,
    errorTestTag: String,
    isError: Boolean,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Box(modifier = Modifier.fillMaxWidth()) {
      OutlinedTextField(
          value = value,
          onValueChange = onValueChange,
          modifier = Modifier.fillMaxWidth().testTag(inputTestTag),
          isError = isError,
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          colors =
              OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = darkGray,
                  unfocusedBorderColor = darkGray.copy(alpha = 0.4f),
              ),
      )
      if (value.isEmpty()) {
        Text(
            text = placeholder,
            color = CreateProfilePlaceholderColor,
            modifier =
                Modifier.align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .testTag(placeholderTestTag),
        )
      }
    }
    if (isError) {
      Text(
          text = FILL_OUT_MESSAGE,
          color = ErrorRed,
          fontSize = 12.sp,
          modifier = Modifier.padding(start = 16.dp, top = 4.dp).testTag(errorTestTag),
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
fun CreateProfilePreview() {
  CreateProfile(viewModel = viewModel())
}
