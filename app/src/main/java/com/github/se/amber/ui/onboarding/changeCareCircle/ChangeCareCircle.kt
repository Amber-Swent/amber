// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.onboarding.changeCareCircle

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.ui.caregiver.DisplayCaregiverTitle
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.CaregiverTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_BIG
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.theme.DIVIDER_THICKNESS
import com.github.se.amber.ui.theme.ROUND_CORNER

/** Tags for the change care circle screen. */
object ChangeCareCircleTestTags {
  const val TITLE = "change_care_circle_title"
  const val CURRENT_CIRCLE = "change_care_circle_current"
  const val CIRCLE_LIST = "change_care_circle_list"
  const val EMPTY_MESSAGE = "change_care_circle_empty_message"
  const val LOADING_INDICATOR = "change_care_circle_loading_indicator"

  fun circleChoice(circleId: String = "") = "change_care_circle_choice_$circleId"
}

/** Displays the active care circle and the caregiver's available circles. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChangeCareCircleScreen(
    viewModel: ChangeCareCircleViewModel = viewModel(),
    onBackClick: () -> Unit = {},
    onTabSelected: (Tab) -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val activeCircleName = uiState.circles.firstOrNull { it.id == uiState.activeCircleId }?.name

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
      },
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = CaregiverTab.Setting,
            onTabSelected = onTabSelected,
            tabs = CaregiverTab.caregiverTabs,
        )
      },
  ) { paddingValues ->
    when (uiState.status) {
      ChangeCareCircleStatus.LOADING, ChangeCareCircleStatus.EMPTY -> {
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
                text = stringResource(R.string.changeCareCircleEmpty),
                modifier = Modifier.testTag(ChangeCareCircleTestTags.EMPTY_MESSAGE),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
          }
        }
      }
      ChangeCareCircleStatus.READY -> {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CAREGIVER_PADDING_MEDIUM, vertical = CAREGIVER_PADDING_BIG),
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
                )
              }
            }
          }
        }
      }
    }
    // TODO display message info
  }
}

@Preview(name = "Loading care circles", showBackground = true, showSystemUi = true)
@Composable
internal fun ChangeCareCircleLoadingScreenPreview() {
    val previewViewModel =
        remember {
            ChangeCareCircleViewModel(
                ChangeCareCircleUIState(status = ChangeCareCircleStatus.LOADING)
            )
        }
    AmberAppTheme(dynamicColor = false) {
        ChangeCareCircleScreen(viewModel = previewViewModel)
    }
}

@Preview(name = "No care circles", showBackground = true, showSystemUi = true)
@Composable
internal fun ChangeCareCircleEmptyScreenPreview() {
  val previewViewModel = remember { ChangeCareCircleViewModel() }
  AmberAppTheme(dynamicColor = false) {
    ChangeCareCircleScreen(viewModel = previewViewModel)
  }
}

@Preview(name = "care circles", showBackground = true, showSystemUi = true)
@Composable
internal fun ChangeCareCircleScreenPreview() {
  val previewViewModel =
      remember {
        ChangeCareCircleViewModel(
            ChangeCareCircleUIState(
                status = ChangeCareCircleStatus.READY,
                circles =
                    listOf(
                        ChangeCareCircleChoice("one", "Cira's circle"),
                        ChangeCareCircleChoice("two", "Eloise's circle"),
                        ChangeCareCircleChoice("three", "Flora's circle"),
                        ChangeCareCircleChoice("four", "Natsu's circle"),
                    ),
                activeCircleId = "two",
            )
        )
      }
  AmberAppTheme(dynamicColor = false) { ChangeCareCircleScreen(viewModel = previewViewModel) }
}
