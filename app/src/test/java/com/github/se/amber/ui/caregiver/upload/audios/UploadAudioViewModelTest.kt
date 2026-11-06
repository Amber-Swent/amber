//Written by Lou-Anne Maier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver.upload.audios

import com.github.se.amber.model.media.AudioRecordingState
import com.github.se.amber.model.media.MemoryDate
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [UploadAudioViewModel]. Every repository is a mock: nothing touches the network.
 *
 * Situation: the audio upload screen can't have its own logic yet. Its ViewModel will be built on a
 * global upload ViewModel, which is not defined yet, so every action of [UploadAudioViewModel] is a
 * `TODO("...")` and throws [NotImplementedError]. The tests below therefore check two things: the
 * initial state, which already exists, and that each action throws. They are drafts that keep the
 * coverage up in the meantime: when an action is implemented, replace its "throws" test with tests
 * of its real behavior.
 */
class UploadAudioViewModelTest {
  private lateinit var viewModel: UploadAudioViewModel

  @Before
  fun setUp() {
    viewModel =
        UploadAudioViewModel(
            mediaRepo = mockk(relaxed = true),
            authRepo = mockk(relaxed = true),
            profileRepo = mockk(relaxed = true),
            circleRepo = mockk(relaxed = true),
            placeRepo = mockk(relaxed = true),
        )
  }

  // ---------- initial state ----------

  @Test
  fun initialState_followUploadCondition() {
    assertEquals(AudioRecordingState.IDLE, viewModel.uiState.value.recordingState)
    assertFalse(viewModel.uiState.value.canUpload)
  }

  // ---------- actions: TODO(), so they throw until implemented ----------

  @Test
  fun clearErrorMsg_isNotImplementedYet_throws() {
    assertThrows(NotImplementedError::class.java) { viewModel.clearErrorMsg() }
  }

  @Test
  fun setErrorMsg_isNotImplementedYet_throws() {
    assertThrows(NotImplementedError::class.java) { viewModel.setErrorMsg("Boom") }
  }

  @Test
  fun setDescription_isNotImplementedYet_throws() {
    assertThrows(NotImplementedError::class.java) { viewModel.setDescription("Grandma's story") }
  }

  @Test
  fun setDate_isNotImplementedYet_throws() {
    assertThrows(NotImplementedError::class.java) { viewModel.setDate(MemoryDate(1974)) }
  }

  @Test
  fun toggleShownId_isNotImplementedYet_throws() {
    assertThrows(NotImplementedError::class.java) { viewModel.toggleShownId("person1") }
  }

  @Test
  fun onAddPerson_isNotImplementedYet_throws() {
    assertThrows(NotImplementedError::class.java) { viewModel.onAddPerson() }
  }

  @Test
  fun setPersonFirstName_isNotImplementedYet_throws() {
    assertThrows(NotImplementedError::class.java) { viewModel.setPersonFirstName("Anna") }
  }

  @Test
  fun setPersonLastName_isNotImplementedYet_throws() {
    assertThrows(NotImplementedError::class.java) { viewModel.setPersonLastName("Smith") }
  }

  @Test
  fun setPersonNickname_isNotImplementedYet_throws() {
    assertThrows(NotImplementedError::class.java) { viewModel.setPersonNickname("Annie") }
  }

  @Test
  fun dismissAddPerson_isNotImplementedYet_throws() {
    assertThrows(NotImplementedError::class.java) { viewModel.dismissAddPerson() }
  }

  @Test
  fun addPerson_isNotImplementedYet_throws() {
    assertThrows(NotImplementedError::class.java) { viewModel.addPerson() }
  }

  @Test
  fun startRecording_isNotImplementedYet_throws() {
    assertThrows(NotImplementedError::class.java) { viewModel.startRecording() }
  }

  @Test
  fun stopRecording_isNotImplementedYet_throws() {
    assertThrows(NotImplementedError::class.java) { viewModel.stopRecording() }
  }

  @Test
  fun deleteRecording_isNotImplementedYet_throws() {
    assertThrows(NotImplementedError::class.java) { viewModel.deleteRecording() }
  }

  @Test
  fun uploadAudio_isNotImplementedYet_throws() {
    assertThrows(NotImplementedError::class.java) { viewModel.uploadAudio() }
  }
}
