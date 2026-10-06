package com.github.se.amber.ui.upload

import com.github.se.amber.ui.caregiver.upload.UploadUIState
import com.github.se.amber.ui.caregiver.upload.UploadViewModel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class UploadViewModelTest {
    private lateinit var viewModel: UploadViewModel
    @Before
    fun setUp() {
        viewModel = UploadViewModel()
    }

    // ---------- UploadUIState ----------
    @Test
    fun uiStateHasNoErrorByDefault() {
        assertNull(UploadUIState().errorMsg)
    }

    @Test
    fun uiStateKeepsGivenErrorMessage() {
        assertEquals("Boom", UploadUIState(errorMsg = "Boom").errorMsg)
    }

    @Test
    fun uiStateCopyCanClearErrorMessage() {
        assertNull(UploadUIState(errorMsg = "Boom").copy(errorMsg = null).errorMsg)
    }

    // ---------- Initial state ----------
    @Test
    fun initialUiStateHasNoError() {
        assertNull(viewModel.uiState.value.errorMsg)
    }

    @Test
    fun initialUiStateEqualsDefaultUiState() {
        assertEquals(UploadUIState(), viewModel.uiState.value)
    }

    // ---------- setErrorMsg ----------
    @Test
    fun setErrorMsgStoresTheMessage() {
        viewModel.setErrorMsg("Upload failed")
        assertEquals("Upload failed", viewModel.uiState.value.errorMsg)
    }

    @Test
    fun setErrorMsgReplacesPreviousMessage() {
        viewModel.setErrorMsg("First error")
        viewModel.setErrorMsg("Second error")
        assertEquals("Second error", viewModel.uiState.value.errorMsg)
    }

    // ---------- clearErrorMsg ----------
    @Test
    fun clearErrorMsgRemovesTheMessage() {
        viewModel.setErrorMsg("Upload failed")
        viewModel.clearErrorMsg()
        assertNull(viewModel.uiState.value.errorMsg)
    }

    @Test
    fun clearErrorMsgWithoutErrorKeepsStateWithoutError() {
        viewModel.clearErrorMsg()
        assertNull(viewModel.uiState.value.errorMsg)
    }

    @Test
    fun clearErrorMsgTwiceStillLeavesNoError() {
        viewModel.setErrorMsg("Upload failed")
        viewModel.clearErrorMsg()
        viewModel.clearErrorMsg()
        assertNull(viewModel.uiState.value.errorMsg)
    }
}