// Written by MeiliV, with assistance from OpenAI Codex
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.onboarding.createCareCircle

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.data.onboarding.OnboardingRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.ui.caregiver.DisplayCaregiverButton
import com.github.se.amber.ui.caregiver.DisplayCaregiverTitle
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.onboarding.AmberHeader
import com.github.se.amber.ui.onboarding.OnboardingOutlinedTextField
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_BIG
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.WEIGHT_ONE
import com.github.se.amber.ui.utils.asString

/** Stable tags for locating the form controls and validation message in UI tests. */
object CreateCareCircleTestTags {
  const val NAME_FIELD = "create_care_circle_name_field"
  const val NAME_ERROR = "create_care_circle_name_error"
}

/**
 * Displays the first care circle form and connects it to [viewModel].
 *
 * A blank or invalid name shows its error below the field and disables Create. Repository failures
 * are shown as a toast, while successful creation calls [onCreated] once. The top Cancel calls
 * [onBack]. Cancel and system back do nothing while a create is in progress or after the circle has
 * been created.
 *
 * @param modifier Modifier applied to the root care circle layout.
 * @param context Used to display repository failures as a toast.
 * @param onCreated Called after the repository creates the circle.
 * @param onBack Called when the user taps Cancel, unless a create is in progress or finished.
 *   System back is ignored in those same states.
 * @param viewModel ViewModel holding the form state and handling creation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCareCircleScreen(
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current,
    onCreated: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: CreateCareCircleViewModel = viewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  // Configuration-aware resources, so the toast follows the app language
  val resources = LocalResources.current

  // Keep one stable reference for the effect, but always update the value inside it to the newest
  // onCreated
  val currentOnCreated by rememberUpdatedState(onCreated)

  // Display submit errors when creating the circle
  LaunchedEffect(viewModel) {
    viewModel.errorEvents.collect { errorRes ->
      Toast.makeText(context, resources.getString(errorRes), Toast.LENGTH_SHORT).show()
    }
  }

  // LaunchedEffect depends only on the ViewModel since depending on the lambda onCreated
  // can make the compose receive a new lambda on recomposition and therefore triggering a new
  // launch
  LaunchedEffect(viewModel) { viewModel.successEvents.collect { currentOnCreated() } }

  val isEditable = !uiState.isLoading && !uiState.isCreated
  BackHandler(enabled = !isEditable) {}

  Scaffold(
      modifier = modifier.fillMaxSize(),
      containerColor = MaterialTheme.colorScheme.background,
      contentColor = MaterialTheme.colorScheme.onBackground,
      topBar = {
        TopAppBar(
            title = {
              TopNavigationMenu(
                  action = TopNavigationAction.CANCEL,
                  onBackClick = { if (isEditable) onBack() },
              )
            }
        )
      },
  ) { innerPadding ->
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CAREGIVER_PADDING_BIG, vertical = CAREGIVER_PADDING_BIG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_BIG),
    ) {
      AmberHeader(horizontalAlignment = Alignment.CenterHorizontally)

      DisplayCaregiverTitle(title = stringResource(R.string.createCareCircleTitle))

      OnboardingOutlinedTextField(
          value = uiState.name,
          onValueChange = viewModel::onNameChange,
          label = stringResource(R.string.createCareCircleNameLabel),
          modifier = Modifier.testTag(CreateCareCircleTestTags.NAME_FIELD),
          placeholder = stringResource(R.string.createCareCircleNamePlaceholder),
          error = uiState.nameError?.asString(resources),
          errorTestTag = CreateCareCircleTestTags.NAME_ERROR,
          enabled = isEditable,
      )

      DisplayCaregiverButton(
          text =
              stringResource(
                  if (uiState.isLoading) R.string.createCareCircleCreating
                  else R.string.createCareCircleCreate
              ),
          enabled = uiState.canCreate,
          onClick = viewModel::createCareCircle,
      )

      Text(
          text = stringResource(R.string.createCareCircleInviteLater),
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
