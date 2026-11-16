// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by camomille299, with assistance from Claude (Anthropic) via claude.ai.

package com.github.se.amber.ui.patient.seeMedias.pictures

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.ui.media.MediaImage
import com.github.se.amber.ui.media.aspectRatioOf
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.patient.seeMedias.medias.SeeGeneralMedias
import com.github.se.amber.ui.patient.seeMedias.medias.SeeGeneralMediasConfig
import com.github.se.amber.ui.theme.AmberAppTheme

object SeePicturesTestTags {
  const val NO_PICTURES = "see_pictures_no_pictures"
  const val ERROR = "see_pictures_error"
  const val TITLE = "see_pictures_title"
  const val LOADING = "see_pictures_loading" // new: the shared screen tags its loader

  fun picture(id: String = "") = "see_pictures_picture_$id"

  fun pictureCard(id: String = "") = "see_pictures_picture_card_$id"
}

/** Texts and test tags of the pictures screen, given to [SeeGeneralMedias]. */
private val PICTURES_SCREEN_CONFIG =
    SeeGeneralMediasConfig(
        titleRes = R.string.seePicturesTitle,
        retryRes = R.string.seePicturesRetry,
        emptyRes = R.string.seePicturesEmpty,
        titleTag = SeePicturesTestTags.TITLE,
        errorTag = SeePicturesTestTags.ERROR,
        emptyTag = SeePicturesTestTags.NO_PICTURES,
        loadingTag = SeePicturesTestTags.LOADING,
        cardTag = { id -> SeePicturesTestTags.pictureCard(id) },
    )

@Composable
fun SeePicturesScreen(
    pictureViewModel: SeePicturesViewModel = viewModel(),
    onBackClick: () -> Unit = {},
    onTabSelected: (Tab) -> Unit = {},
) {
  val uiState by pictureViewModel.uiState.collectAsStateWithLifecycle()

  SeeGeneralMedias(
      uiState = uiState,
      config = PICTURES_SCREEN_CONFIG,
      onRetry = pictureViewModel::retry,
      onErrorShown = pictureViewModel::clearErrorMsg,
      onBackClick = onBackClick,
      onTabSelected = onTabSelected,
  ) { picture, circle, modifier ->
    MediaImage(
        circle = circle,
        picture = picture,
        modifier = modifier.aspectRatioOf(picture).testTag(SeePicturesTestTags.picture(picture.id)),
        loading = { CircularProgressIndicator() },
    )
  }
}

@Preview(showBackground = true)
@Composable
fun SeePicturesScreenPreview() {
  AmberAppTheme { SeePicturesScreen(onBackClick = {}, onTabSelected = {}) }
}
