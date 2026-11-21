// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.

package com.github.se.amber.ui.patient.seeMedias.medias

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.ui.media.MediaCard
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.patient.DisplayPatientButton
import com.github.se.amber.ui.patient.DisplayPatientTitle
import com.github.se.amber.ui.theme.PATIENT_BUTTON_WIDTH_BIG
import com.github.se.amber.ui.theme.PATIENT_PADDING_MEDIUM
import com.github.se.amber.ui.theme.PATIENT_PADDING_SMALL

/**
 * What differs between two "See medias" screens apart from how one item is drawn: texts and test
 * tags. No parameter has a default value on purpose: each one belongs to a given screen, so a
 * default would let a new screen silently show another screen's text or reuse its test tags. The
 * compiler makes every screen provide all of them.
 *
 * @param titleRes title of the screen.
 * @param retryRes text of the Retry button of the blocking error.
 * @param emptyRes message shown when there is no media of this kind.
 * @param titleTag test tag of the title.
 * @param errorTag test tag of the blocking error message.
 * @param emptyTag test tag of the empty message.
 * @param loadingTag test tag of the loading indicator.
 * @param cardTag test tag of the [MediaCard] below the item with the given media ID.
 */
data class SeeGeneralMediasConfig(
    @param:StringRes val titleRes: Int,
    @param:StringRes val retryRes: Int,
    @param:StringRes val emptyRes: Int,
    val titleTag: String,
    val errorTag: String,
    val emptyTag: String,
    val loadingTag: String,
    val cardTag: (String) -> String,
)

/**
 * Scaffold, loading / blocking error / empty states, error Toast and scrollable list shared by the
 * patient "See medias" screens. Each item is its media ([itemContent]) followed by its [MediaCard].
 *
 * [uiState], [config] and [itemContent] have no default value on purpose: there is no sensible
 * state to show, no texts that fit every kind of media, and the general screen cannot draw a media
 * by itself. The callbacks do nothing by default, so a caller (or a test) only gives the ones it
 * needs.
 *
 * @param uiState the state to display.
 * @param config texts and test tags of this kind of media.
 * @param onRetry called when the Retry button of the blocking error is pressed.
 * @param onErrorShown called once the non-blocking error was shown in a Toast.
 * @param onBackClick called when the top navigation action is selected.
 * @param onTabSelected called when a bottom navigation tab is selected.
 * @param titleArgs format arguments of the title string ([SeeGeneralMediasConfig.titleRes]), for a
 *   title that contains a value such as a name. Empty when the title is a plain text.
 * @param itemContent draws one media; use the given modifier (it carries the common width).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : MediaItem> SeeGeneralMedias(
    uiState: SeeGeneralMediasUIState<T>,
    config: SeeGeneralMediasConfig,
    onRetry: () -> Unit = {},
    onErrorShown: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onTabSelected: (Tab) -> Unit = {},
    titleArgs: List<Any> = emptyList(),
    itemContent: @Composable (item: T, circle: CareCircle, modifier: Modifier) -> Unit,
) {
  val context = LocalContext.current
  // Configuration-aware resources, so the toast follows the app language
  val resources = LocalResources.current

  // Non-blocking error message: only Empty and Content can carry one
  val errorMsgRes =
      when (uiState) {
        is SeeGeneralMediasUIState.Content -> uiState.errorMsgRes
        is SeeGeneralMediasUIState.Empty -> uiState.errorMsgRes
        is SeeGeneralMediasUIState.Loading,
        is SeeGeneralMediasUIState.BlockingError -> null
      }

  LaunchedEffect(errorMsgRes) {
    if (errorMsgRes != null) {
      Toast.makeText(context, resources.getString(errorMsgRes), Toast.LENGTH_LONG).show()
      onErrorShown()
    }
  }

  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Column {
                TopNavigationMenu(
                    modifier = Modifier.testTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU),
                    action = TopNavigationAction.CANCEL,
                    onBackClick = onBackClick,
                )
                DisplayPatientTitle(
                    title = stringResource(config.titleRes, *titleArgs.toTypedArray()),
                    testTag = config.titleTag,
                )
              }
            }
        )
      },
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = PatientTab.SeeMedias,
            onTabSelected = onTabSelected,
            tabs = PatientTab.patientTabs,
            modifier = Modifier.testTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU),
        )
      },
      content = { paddingValues ->
        when (uiState) {
          is SeeGeneralMediasUIState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator(modifier = Modifier.testTag(config.loadingTag))
            }
          }

          is SeeGeneralMediasUIState.BlockingError -> {
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(paddingValues)
                        .padding(PATIENT_PADDING_MEDIUM)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement =
                    Arrangement.spacedBy(PATIENT_PADDING_SMALL, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                  modifier = Modifier.testTag(config.errorTag),
                  text = stringResource(uiState.errorMsgRes),
                  textAlign = TextAlign.Center,
                  color = MaterialTheme.colorScheme.error,
                  style = MaterialTheme.typography.titleLarge,
              )
              DisplayPatientButton(text = stringResource(config.retryRes), onClick = onRetry)
            }
          }

          is SeeGeneralMediasUIState.Empty -> {
            Box(
                modifier =
                    Modifier.fillMaxSize().padding(paddingValues).padding(PATIENT_PADDING_MEDIUM),
                contentAlignment = Alignment.Center,
            ) {
              Text(
                  modifier = Modifier.testTag(config.emptyTag),
                  text = stringResource(config.emptyRes),
                  textAlign = TextAlign.Center,
                  color = MaterialTheme.colorScheme.onBackground,
                  style = MaterialTheme.typography.titleLarge,
              )
            }
          }

          is SeeGeneralMediasUIState.Content -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(PATIENT_PADDING_SMALL),
                verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL),
            ) {
              items(uiState.items, key = { it.id }) { item ->
                // One Column so the media and its MediaCard do not touch
                Column(verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL)) {
                  itemContent(
                      item,
                      uiState.careCircle,
                      Modifier.fillMaxWidth(PATIENT_BUTTON_WIDTH_BIG),
                  )
                  MediaCard(
                      modifier = Modifier.fillMaxWidth(PATIENT_BUTTON_WIDTH_BIG),
                      media = item,
                      testTag = config.cardTag(item.id),
                  )
                }
              }
            }
          }
        }
      },
  )
}
