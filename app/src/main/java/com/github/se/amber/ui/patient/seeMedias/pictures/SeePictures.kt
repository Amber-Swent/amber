// this code was written with the aid of AI
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.patient.seeMedias.pictures

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.ui.media.MediaImage
import com.github.se.amber.ui.media.aspectRatioOf
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.patient.DisplayPatientButton
import com.github.se.amber.ui.patient.DisplayPatientTitle
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.PATIENT_PADDING_MEDIUM
import com.github.se.amber.ui.theme.PATIENT_PADDING_SMALL
import com.github.se.amber.ui.theme.WEIGHT_ONE

object SeePicturesTestTags {
  const val NO_PICTURES_SEE_PICTURES = "no_pictures_see_pictures"
  const val ERROR_SEE_PICTURES = "pictures_error_see_pictures"

  const val TITLE_SEE_PICTURES = "title_see_pictures"

  fun picture(id: String = "") = "picture_$id"
}

@Composable
fun SeePicturesScreen(
    pictureViewModel: SeePicturesViewModel = viewModel(),
    onBackClick: () -> Unit = {},
    onTabSelected: (Tab) -> Unit = {},
) {

  val context = LocalContext.current
  val uiState by pictureViewModel.uiState.collectAsStateWithLifecycle()

  // Non-blocking error message: only Empty and Content can carry one
  val errorMsg =
      when (val state = uiState) {
        is SeePicturesUIState.Content -> state.errorMsg
        is SeePicturesUIState.Empty -> state.errorMsg
        is SeePicturesUIState.Loading,
        is SeePicturesUIState.BlockingError -> null
      }

  // Show error message if fetching todos fails
  LaunchedEffect(errorMsg) {
    errorMsg?.let { message ->
      Toast.makeText(context, message, Toast.LENGTH_LONG).show()
      pictureViewModel.clearErrorMsg()
    }
  }

  Scaffold(
      topBar = {
        Column {
          TopNavigationMenu(
              modifier = Modifier.testTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU),
              action = TopNavigationAction.CANCEL,
              onBackClick = onBackClick,
          )
          DisplayPatientTitle(
              title = "See Pictures",
              testTag = SeePicturesTestTags.TITLE_SEE_PICTURES,
          )
        }
      },
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = PatientTab.SeePictures,
            onTabSelected = onTabSelected,
            tabs = PatientTab.patientTabs,
            modifier = Modifier.testTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU),
        )
      },
      content = { paddingValues ->
        when (val actualState = uiState) {
          is SeePicturesUIState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator()
            }
          }

          is SeePicturesUIState.BlockingError -> {
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
                  modifier = Modifier.testTag(SeePicturesTestTags.ERROR_SEE_PICTURES),
                  text = actualState.message,
                  textAlign = TextAlign.Center,
                  color = MaterialTheme.colorScheme.error,
                  style = MaterialTheme.typography.titleLarge,
              )
              DisplayPatientButton(
                  text = "Retry",
                  onClick = { pictureViewModel.retry() },
              )
            }
          }

          is SeePicturesUIState.Empty -> {
            Box(
                modifier =
                    Modifier.fillMaxSize().padding(paddingValues).padding(PATIENT_PADDING_MEDIUM),
                contentAlignment = Alignment.Center,
            ) {
              Text(
                  modifier = Modifier.testTag(SeePicturesTestTags.NO_PICTURES_SEE_PICTURES),
                  text = "There are no pictures yet.",
                  textAlign = TextAlign.Center,
                  color = MaterialTheme.colorScheme.onBackground,
                  style = MaterialTheme.typography.titleLarge,
              )
            }
          }

          is SeePicturesUIState.Content -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL),
            ) {
              items(actualState.allPictures, key = { it.id }) { picture ->
                MediaImage(
                    circle = actualState.careCircle,
                    picture = picture,
                    modifier =
                        Modifier.fillMaxWidth(WEIGHT_ONE)
                            .aspectRatioOf(picture)
                            .testTag(SeePicturesTestTags.picture(picture.id)),
                    loading = { CircularProgressIndicator() },
                )
              }
            }
          }
        }
      },
  )
}

@Preview(showBackground = true)
@Composable
fun SeePicturesScreenPreview() {
  AmberAppTheme {
    SeePicturesScreen(
        onBackClick = {},
        onTabSelected = {},
    )
  }
}
