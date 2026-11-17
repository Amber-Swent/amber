// Written by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.caregiver.upload.text

import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.ui.caregiver.upload.media.CommonMediaUIState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks the text upload state and that the named actions do not run yet. */
class UploadTextViewModelTest {
  @Test
  fun namedFunctionsLeaveTheStoryUntouched() {
    val viewModel = UploadTextViewModel()

    viewModel.setTitle("Market day")
    viewModel.setText("We bought bread.")
    viewModel.setDate(MemoryDate(2020))
    viewModel.setPlaceQuery("Lausanne")
    viewModel.onAddPerson()
    viewModel.setPersonFirstName("Ada")
    viewModel.setPersonLastName("Lovelace")
    viewModel.setPersonNickname("Ada")
    viewModel.addPerson()
    viewModel.dismissAddPerson()
    viewModel.clearErrorMsg()
    viewModel.uploadText()

    assertEquals(UploadTextUIState(), viewModel.uiState.value)
    assertEquals("", viewModel.mediaDescription())
  }

  @Test
  fun uploadNeedsATitleAndTheStoryText() {
    assertFalse(UploadTextUIState().canUpload)
    assertFalse(UploadTextUIState(title = "   ").canUpload)
    assertFalse(UploadTextUIState(text = "   ").canUpload)
    assertFalse(
        UploadTextUIState(
                title = "Market day",
                common = CommonMediaUIState(description = "We bought bread."),
            )
            .canUpload,
    )
    assertTrue(UploadTextUIState(title = "Market day", text = "We bought bread.").canUpload)
  }
}
