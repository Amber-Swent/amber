package com.github.se.amber.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.ui.patient.PatientUtilsTestTags

/**
 * Displays the card related to the Media, including the date, the description, the location and the
 * persons related to it
 *
 * This functions must only be used on scrollable screens.
 *
 * @param modifier modifier to apply to the card
 * @param media the media item we want to apply the description of. By default a picture.
 * @param testTag test tag of the card
 */
@Composable
fun DisplayMediaCard(
    modifier: Modifier = Modifier,
    media: MediaItem = MediaItem.Picture(),
    testTag: String = PatientUtilsTestTags.TITLE,
    mediaCardViewModel: MediaCardViewModel = viewModel(),
) {
  val uiState by mediaCardViewModel.uiState.collectAsStateWithLifecycle()
}
