// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.caregiver.parameters.changeCareCircle

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.github.se.amber.ui.caregiver.DisplayCaregiverButton
import com.github.se.amber.ui.caregiver.DisplayCaregiverTitle
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_BIG
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.theme.DIVIDER_THICKNESS
import com.github.se.amber.ui.theme.ROUND_CORNER
import com.github.se.amber.ui.utils.selectableChipColors

/** Tags for the change care circle screen. */
object ChangeCareCircleTestTags {
  const val TITLE = "change_care_circle_title"
  const val CURRENT_CIRCLE = "change_care_circle_current"
  const val CIRCLE_LIST = "change_care_circle_list"
  const val EMPTY_MESSAGE = "change_care_circle_empty_message"
  const val LOADING_INDICATOR = "change_care_circle_loading_indicator"
  const val ERROR_MESSAGE = "change_care_circle_error_message"
  const val RETRY_BUTTON = "change_care_circle_retry_button"

  /**
   * Builds the test tag for a care circle chip.
   *
   * @param circleId ID of the circle represented by the chip.
   * @return The chip's stable test tag.
   */
  fun circleChoice(circleId: String = "") = "change_care_circle_choice_$circleId"
}

/**
 * Displays the care circles available to the caregiver.
 *
 * The screen collects [ChangeCareCircleViewModel.uiState] with lifecycle awareness. Loading shows a
 * centered indicator; empty shows a centered message without circle details; ready shows the active
 * circle name and one chip per available circle. If the active ID matches no circle, the
 * current-circle value uses a localized fallback and no chip is selected. Chip clicks call
 * [ChangeCareCircleViewModel.changeActiveCareCircle].
 *
 * @param viewModel Provides the screen state and receives chip selections.
 * @param onBackClick Called when the top-bar back button is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChangeCareCircleScreen(
    viewModel: ChangeCareCircleViewModel = viewModel(),
    onBackClick: () -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val activeCircleName = uiState.circles.firstOrNull { it.id == uiState.activeCircleId }?.name
  val context = LocalContext.current
  val resources = LocalResources.current

  LaunchedEffect(uiState.errorMsgRes, uiState.status) {
    if (uiState.status == ChangeCareCircleStatus.READY) {
      uiState.errorMsgRes?.let { errorMsgRes ->
        Toast.makeText(context, resources.getString(errorMsgRes), Toast.LENGTH_SHORT).show()
        viewModel.clearErrorMsg()
      }
    }
  }

  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Column(modifier = Modifier.fillMaxWidth().padding(CAREGIVER_PADDING_SMALL)) {
                TopNavigationMenu(
                    action = TopNavigationAction.CANCEL,
                    onBackClick = onBackClick,
                )
                DisplayCaregiverTitle(
                    modifier = Modifier.testTag(ChangeCareCircleTestTags.TITLE),
                    title = stringResource(R.string.changeCareCircleTitle),
                )
              }
            }
        )
      }
  ) { paddingValues ->
    when (uiState.status) {
      ChangeCareCircleStatus.LOADING,
      ChangeCareCircleStatus.EMPTY -> {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = CAREGIVER_PADDING_MEDIUM),
            contentAlignment = Alignment.Center,
        ) {
          if (uiState.status == ChangeCareCircleStatus.LOADING) {
            CircularProgressIndicator(
                modifier = Modifier.testTag(ChangeCareCircleTestTags.LOADING_INDICATOR),
                color = MaterialTheme.colorScheme.primary,
            )
          } else {
            Text(
                text =
                    stringResource(
                        R.string.changeCareCircleEmpty,
                        stringResource(R.string.navigationTabSetting),
                        stringResource(R.string.caregiverParameterCreateCareCircle),
                        stringResource(R.string.caregiverParameterEnterInvitation),
                    ),
                modifier = Modifier.testTag(ChangeCareCircleTestTags.EMPTY_MESSAGE),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
          }
        }
      }

      // Display an error message and a Retry button
      ChangeCareCircleStatus.ERROR -> {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(paddingValues)
                    .padding(CAREGIVER_PADDING_MEDIUM)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement =
                Arrangement.spacedBy(CAREGIVER_PADDING_SMALL, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
              text =
                  stringResource(uiState.errorMsgRes ?: R.string.changeCareCircleErrorLoadCircle),
              modifier = Modifier.testTag(ChangeCareCircleTestTags.ERROR_MESSAGE),
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.titleMedium,
              textAlign = TextAlign.Center,
          )
          DisplayCaregiverButton(
              text = stringResource(R.string.changeCareCircleRetry),
              onClick = viewModel::loadCareCircles,
              buttonTestTag = ChangeCareCircleTestTags.RETRY_BUTTON,
          )
        }
      }
      ChangeCareCircleStatus.READY -> {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = CAREGIVER_PADDING_MEDIUM,
                        vertical = CAREGIVER_PADDING_BIG,
                    ),
            verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_BIG),
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL)) {
            Text(
                text = stringResource(R.string.changeCareCircleCurrent),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = activeCircleName ?: stringResource(R.string.changeCareCircleNone),
                modifier = Modifier.testTag(ChangeCareCircleTestTags.CURRENT_CIRCLE),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
            )
          }
          Column(verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL)) {
            Text(
                text = stringResource(R.string.changeCareCircleAll),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
            )
            FlowRow(
                modifier =
                    Modifier.fillMaxWidth()
                        .border(
                            DIVIDER_THICKNESS,
                            MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(ROUND_CORNER),
                        )
                        .padding(CAREGIVER_PADDING_MEDIUM)
                        .testTag(ChangeCareCircleTestTags.CIRCLE_LIST),
                horizontalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL),
                verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL),
            ) {
              uiState.circles.forEach { circle ->
                FilterChip(
                    selected = circle.id == uiState.activeCircleId,
                    onClick = { viewModel.changeActiveCareCircle(circle.id) },
                    label = { Text(circle.name, style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.testTag(ChangeCareCircleTestTags.circleChoice(circle.id)),
                    colors = selectableChipColors(),
                )
              }
            }
          }
        }
      }
    }
    // TODO display message info: the helper function needed is currently under review so it cannot
    // be used now.
  }
}

@Preview(name = "Loading care circles", showBackground = true, showSystemUi = true)
@Composable
internal fun ChangeCareCircleLoadingScreenPreview() {
  val previewViewModel = remember {
    // Not needed to precise the status since it's the default one but it makes the preview more
    // robust
    ChangeCareCircleViewModel(ChangeCareCircleUIState(status = ChangeCareCircleStatus.LOADING))
  }
  AmberAppTheme(dynamicColor = false) { ChangeCareCircleScreen(viewModel = previewViewModel) }
}

@Preview(name = "No care circles", showBackground = true, showSystemUi = true)
@Composable
internal fun ChangeCareCircleEmptyScreenPreview() {
  val previewViewModel = remember {
    ChangeCareCircleViewModel(ChangeCareCircleUIState(status = ChangeCareCircleStatus.EMPTY))
  }
  AmberAppTheme(dynamicColor = false) { ChangeCareCircleScreen(viewModel = previewViewModel) }
}

@Preview(name = "error", showBackground = true, showSystemUi = true)
@Composable
internal fun ChangeCareCircleErrorScreenPreview() {
  val previewViewModel = remember {
    ChangeCareCircleViewModel(ChangeCareCircleUIState(status = ChangeCareCircleStatus.ERROR))
  }
  AmberAppTheme(dynamicColor = false) { ChangeCareCircleScreen(viewModel = previewViewModel) }
}

@Preview(name = "care circles", showBackground = true, showSystemUi = true)
@Composable
internal fun ChangeCareCircleScreenPreview() {
  val previewViewModel = remember {
    ChangeCareCircleViewModel(
        ChangeCareCircleUIState(
            status = ChangeCareCircleStatus.READY,
            circles =
                listOf(
                    ChangeCareCircleChoice("one", "Cira's circle"),
                    ChangeCareCircleChoice("two", "Eloise's circle"),
                    ChangeCareCircleChoice("three", "Flora's circle"),
                    ChangeCareCircleChoice("four", "Naya's circle"),
                ),
            activeCircleId = "two",
        )
    )
  }
  AmberAppTheme(dynamicColor = false) { ChangeCareCircleScreen(viewModel = previewViewModel) }
}
