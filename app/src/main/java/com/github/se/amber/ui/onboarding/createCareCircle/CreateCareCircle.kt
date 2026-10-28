// This file was written with the aid of AI.
// Modified by Yuhan YANG, with assistance from Cursor.
package com.github.se.amber.ui.onboarding.createCareCircle

import android.content.Context
import android.widget.Toast
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.data.onboarding.OnboardingRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.ui.caregiver.CaregiverButton
import com.github.se.amber.ui.onboarding.AmberHeader
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_BIG
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.ROUND_CORNER
import com.github.se.amber.ui.theme.WEIGHT_ONE

/** Stable tags for locating the form controls and validation message in UI tests. */
object CreateCareCircleTestTags {
  const val NAME_FIELD = "CreateCareCircle_test_tag_name_field"
  const val NAME_ERROR = "CreateCareCircle_test_tag_name_error"
}

/**
 * Displays the first care circle form and connects it to [viewModel].
 *
 * A blank name appears below the field. Repository failures are shown as a toast, while successful
 * creation calls [onCreated] once. The caller decides where to navigate afterward.
 *
 * @param modifier Modifier applied to the root care circle layout.
 * @param context Used to display repository failures as a toast.
 * @param onCreated Called after the repository creates the circle.
 * @param viewModel ViewModel holding the form state and handling creation.
 */
@Composable
fun CreateCareCircleScreen(
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current,
    onCreated: () -> Unit = {},
    viewModel: CreateCareCircleViewModel = viewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  // Keep one stable reference for the effect, but always update the value inside it to the newest
  // onCreated
  val currentOnCreated by rememberUpdatedState(onCreated)

  // Display submit errors when creating the circle
  LaunchedEffect(viewModel) {
    viewModel.errorEvents.collect { message ->
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
  }

  // LaunchedEffect depends only on the ViewModel since depending on the lambda onCreated
  // can make the compose receive a new lambda on recomposition and therefore triggering a new
  // launch
  LaunchedEffect(viewModel) { viewModel.successEvents.collect { currentOnCreated() } }

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
      AmberHeader(horizontalAlignment = Alignment.CenterHorizontally)

      Text(
          text = "New Care Circle",
          modifier = Modifier.fillMaxWidth(WEIGHT_ONE).padding(top = CAREGIVER_PADDING_MEDIUM),
          style = MaterialTheme.typography.headlineSmall,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.primary,
      )

      OutlinedTextField(
          value = uiState.name,
          onValueChange = viewModel::onNameChange,
          enabled = !uiState.isLoading && !uiState.isCreated,
          modifier = Modifier.fillMaxWidth(WEIGHT_ONE).testTag(CreateCareCircleTestTags.NAME_FIELD),
          label = { Text("Circle name", style = MaterialTheme.typography.labelMedium) },
          placeholder = {
            Text("Name your care circle", style = MaterialTheme.typography.labelMedium)
          },
          singleLine = true,
          supportingText =
              uiState.nameError?.let { error ->
                {
                  Text(
                      text = error,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.error,
                      modifier = Modifier.testTag(CreateCareCircleTestTags.NAME_ERROR),
                  )
                }
              },
          isError = uiState.nameError != null,
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

      CaregiverButton(
          text = if (uiState.isLoading) "Creation..." else "Create",
          enabled = !uiState.isLoading && !uiState.isCreated,
          onClick = viewModel::createCareCircle,
      )

      Text(
          text = "You will be able to invite members to the circle after its creation.",
          modifier = Modifier.fillMaxWidth(WEIGHT_ONE).padding(top = CAREGIVER_PADDING_MEDIUM),
          style = MaterialTheme.typography.bodyMedium,
          textAlign = TextAlign.Center,
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
fun CreateCareCirclePreview() {
  val fakeRepository =
      object : OnboardingRepository {
        override suspend fun createInitialCareCircle(name: String): Result<CareCircle> =
            Result.success(CareCircle(id = "preview-1", name = name))
      }
  val viewModel = remember { CreateCareCircleViewModel(repository = fakeRepository) }
  AmberAppTheme { CreateCareCircleScreen(viewModel = viewModel) }
}
