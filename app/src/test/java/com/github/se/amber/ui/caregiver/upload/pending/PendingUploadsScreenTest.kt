// Written by Alaabrine, with assistance from Cursor.
package com.github.se.amber.ui.caregiver.upload.pending

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.OutboxEntry
import com.github.se.amber.model.media.SendState
import com.github.se.amber.ui.theme.AmberAppTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PendingUploadsScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var viewModel: PendingUploadsViewModel
  private lateinit var stateFlow: MutableStateFlow<PendingUploadsUIState>

  private val failed =
      OutboxEntry(MediaItem.Text(id = "failed", description = "A note"), SendState.FAILED)

  @Before
  fun setUp() {
    stateFlow =
        MutableStateFlow(
            PendingUploadsUIState(
                isLoading = false,
                entries =
                    listOf(
                        OutboxEntry(MediaItem.Text(id = "waiting"), SendState.WAITING),
                        OutboxEntry(MediaItem.Audio(id = "sending"), SendState.SENDING),
                        failed,
                    ),
            )
        )
    viewModel = mockk(relaxed = true)
    every { viewModel.uiState } returns stateFlow
  }

  private fun setScreen() {
    composeTestRule.setContent { AmberAppTheme { PendingUploadsScreen(viewModel = viewModel) } }
  }

  @Test
  fun failedRowShowsRetryAndDiscard() {
    setScreen()

    composeTestRule
        .onNodeWithTag(PendingUploadsTestTags.status("waiting"))
        .assertTextEquals("Waiting to send")
    composeTestRule
        .onNodeWithTag(PendingUploadsTestTags.status("sending"))
        .assertTextEquals("Sending")
    composeTestRule
        .onNodeWithTag(PendingUploadsTestTags.status("failed"))
        .assertTextEquals("Failed")
    composeTestRule.onNodeWithTag(PendingUploadsTestTags.retry("waiting")).assertDoesNotExist()
    composeTestRule.onNodeWithTag(PendingUploadsTestTags.retry("failed")).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(PendingUploadsTestTags.discard("failed"))
        .assertIsDisplayed()
        .performClick()

    verify(exactly = 1) { viewModel.requestDiscard("failed") }
  }
}
