// this code was written with the aid of AI

package com.github.se.amber.ui.patient.seeMedias.pictures
package com.github.se.amber.ui.patient.seePictures

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
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
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.PADDING_BIG
import com.github.se.amber.ui.theme.PADDING_SMALL
import com.github.se.amber.ui.theme.darkGray
import com.github.se.amber.ui.theme.orange

object SeePicturesScreenTestTags {
  const val TITLE = "pictures_title"
  const val NO_PICTURES = "no_pictures_text"
  const val PICTURES_LIST = "pictures_list"
  const val ERROR = "pictures_error_text"
  const val RETRY = "pictures_retry_button"

  fun picture(id: String) = "picture_$id"
}

@Composable
fun SeePicturesScreen(
    pictureViewModel: SeePicturesViewModel = viewModel(),
    onBackClick: () -> Unit,
    onTabSelected: (Tab) -> Unit,
) {

  val context = LocalContext.current
  val uiState by pictureViewModel.uiState.collectAsState()

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
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
      pictureViewModel.clearErrorMsg()
    }
  }

  Scaffold(
      topBar = {
        Column() {
          TopNavigationMenu(
              modifier = Modifier.testTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU),
              action = TopNavigationAction.CANCEL,
              onBackClick = onBackClick,
          )
          Spacer(modifier = Modifier.height(PADDING_SMALL))
          Text(
              modifier = Modifier.fillMaxWidth().testTag(SeePicturesScreenTestTags.TITLE),
              text = "See Pictures",
              textAlign = TextAlign.Center,
              color = orange,
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
          )
          Spacer(modifier = Modifier.height(PADDING_BIG))
        }
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
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(PADDING_BIG),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                  modifier = Modifier.testTag(SeePicturesScreenTestTags.ERROR),
                  text = actualState.message,
                  textAlign = TextAlign.Center,
                  color = darkGray,
                  style = MaterialTheme.typography.titleMedium,
              )
              TextButton(
                  modifier = Modifier.testTag(SeePicturesScreenTestTags.RETRY),
                  onClick = { pictureViewModel.retry() },
              ) {
                Text("Retry")
              }
            }
          }

          is SeePicturesUIState.Empty -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(PADDING_BIG),
                contentAlignment = Alignment.Center,
            ) {
              Text(
                  modifier = Modifier.testTag(SeePicturesScreenTestTags.NO_PICTURES),
                  text = "There are no pictures yet.",
                  textAlign = TextAlign.Center,
                  color = darkGray,
                  style = MaterialTheme.typography.titleLarge,
              )
            }
          }

          is SeePicturesUIState.Content -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(PADDING_SMALL),
            ) {
              items(actualState.allPictures, key = { it.id }) { picture ->
                MediaImage(
                    circle = actualState.careCircle,
                    picture = picture,
                    modifier = Modifier.fillMaxWidth().aspectRatioOf(picture),
                    loading = { CircularProgressIndicator() },
                    failed = { retry -> TextButton(onClick = retry) { Text("Retry") } },
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
