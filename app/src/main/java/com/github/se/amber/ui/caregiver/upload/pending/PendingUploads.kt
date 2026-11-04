// Written by Alaabrine, with assistance from Cursor.
package com.github.se.amber.ui.caregiver.upload.pending

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.OutboxEntry
import com.github.se.amber.model.media.SendState
import com.github.se.amber.ui.caregiver.DisplayCaregiverTitle
import com.github.se.amber.ui.media.MediaImage
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL

/** Test tags of [PendingUploadsScreen]. */
object PendingUploadsTestTags {
  const val TITLE = "pending_uploads_title"
  const val EMPTY = "pending_uploads_empty"
  const val ERROR = "pending_uploads_error"
  const val CONFIRM_DISCARD = "pending_uploads_confirm_discard"

  /** Tag of the row for [mediaId]. */
  fun row(mediaId: String) = "pending_uploads_row_$mediaId"

  /** Tag of the status label for [mediaId]. */
  fun status(mediaId: String) = "pending_uploads_status_$mediaId"

  /** Tag of the retry button for [mediaId]. */
  fun retry(mediaId: String) = "pending_uploads_retry_$mediaId"

  /** Tag of the discard button for [mediaId]. */
  fun discard(mediaId: String) = "pending_uploads_discard_$mediaId"
}

/** What the caregiver reads for [state]. */
fun pendingUploadStatus(state: SendState): String =
    when (state) {
      SendState.WAITING -> "Waiting to send"
      SendState.SENDING -> "Sending"
      SendState.FAILED -> "Failed"
    }

/**
 * Lists media saved on this phone that has not reached Firebase yet.
 *
 * @param viewModel ViewModel that loads the outbox and handles retry and discard.
 * @param goBack Called when the user leaves the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingUploadsScreen(
    viewModel: PendingUploadsViewModel = viewModel(),
    goBack: () -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  if (uiState.discardMediaId != null) {
    AlertDialog(
        onDismissRequest = viewModel::dismissDiscard,
        title = { Text("Discard this upload?") },
        text = { Text("It will be deleted from this phone and will not be shared.") },
        confirmButton = {
          TextButton(
              onClick = viewModel::confirmDiscard,
              modifier = Modifier.testTag(PendingUploadsTestTags.CONFIRM_DISCARD),
          ) {
            Text("Discard")
          }
        },
        dismissButton = { TextButton(onClick = viewModel::dismissDiscard) { Text("Cancel") } },
    )
  }

  Scaffold(
      topBar = {
        Column(modifier = Modifier.fillMaxWidth().padding(CAREGIVER_PADDING_SMALL)) {
          TopNavigationMenu(Modifier, TopNavigationAction.CANCEL, goBack)
          DisplayCaregiverTitle(
              Modifier.testTag(PendingUploadsTestTags.TITLE),
              "Pending uploads",
          )
        }
      },
      content = { paddingValues ->
        when {
          uiState.isLoading ->
              Column(
                  modifier = Modifier.fillMaxSize().padding(paddingValues),
                  verticalArrangement = Arrangement.Center,
                  horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                CircularProgressIndicator()
              }
          else ->
              LazyColumn(
                  modifier =
                      Modifier.fillMaxSize()
                          .padding(paddingValues)
                          .padding(horizontal = CAREGIVER_PADDING_MEDIUM),
                  verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_MEDIUM),
              ) {
                uiState.errorMsg?.let { message ->
                  item {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(top = CAREGIVER_PADDING_SMALL)
                                .testTag(PendingUploadsTestTags.ERROR),
                    )
                  }
                }
                if (uiState.entries.isEmpty()) {
                  item {
                    Text(
                        text = "No uploads waiting to send.",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(top = CAREGIVER_PADDING_MEDIUM)
                                .testTag(PendingUploadsTestTags.EMPTY),
                    )
                  }
                }
                items(uiState.entries, key = { it.item.id }) { entry ->
                  PendingUploadRow(
                      circle = uiState.circle,
                      entry = entry,
                      onRetry = { viewModel.retry(entry.item.id) },
                      onDiscard = { viewModel.requestDiscard(entry.item.id) },
                  )
                }
              }
        }
      },
  )
}

@Composable
private fun PendingUploadRow(
    circle: CareCircle?,
    entry: OutboxEntry,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
) {
  val mediaId = entry.item.id
  Column(
      modifier = Modifier.fillMaxWidth().testTag(PendingUploadsTestTags.row(mediaId)),
      verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL),
  ) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      val picture = entry.item as? MediaItem.Picture
      if (picture != null && circle != null) {
        MediaImage(
            circle = circle,
            picture = picture,
            modifier = Modifier.size(72.dp),
            contentDescription = picture.description.ifBlank { "Photo" },
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
            text = entry.item.description.ifBlank { kindLabel(entry.item) },
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = pendingUploadStatus(entry.state),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(PendingUploadsTestTags.status(mediaId)),
        )
      }
    }
    if (entry.state == SendState.FAILED) {
      Row(horizontalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL)) {
        TextButton(
            onClick = onRetry,
            modifier = Modifier.testTag(PendingUploadsTestTags.retry(mediaId)),
        ) {
          Text("Retry")
        }
        TextButton(
            onClick = onDiscard,
            modifier = Modifier.testTag(PendingUploadsTestTags.discard(mediaId)),
        ) {
          Text("Discard")
        }
      }
    }
  }
}

private fun kindLabel(item: MediaItem): String =
    when (item) {
      is MediaItem.Picture -> "Photo"
      is MediaItem.Audio -> "Voice memo"
      is MediaItem.Text -> "Text"
    }
