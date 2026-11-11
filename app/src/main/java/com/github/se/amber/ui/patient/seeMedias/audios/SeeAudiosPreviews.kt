// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.patient.seeMedias.audios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.media.audio.AudioPlayer
import com.github.se.amber.data.media.audio.AudioState
import com.github.se.amber.data.media.audio.WAVEFORM_BARS
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.CircleMedia
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.OutboxEntry
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.media.MediaCardViewModel
import com.github.se.amber.ui.theme.AUDIO_WAVEFORM_MIN_LEVEL
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.HALF
import com.github.se.amber.ui.theme.WEIGHT_ONE
import java.io.File
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

private enum class PreviewAudioState {
  Loading,
  Empty,
  Error,
  Content,
}

private val previewWaveformPattern = listOf(HALF, WEIGHT_ONE, HALF, AUDIO_WAVEFORM_MIN_LEVEL)
private val previewWaveform =
    List(WAVEFORM_BARS) { previewWaveformPattern[it % previewWaveformPattern.size] }

/** Only the approved-media observation is used by this preview. */
private class PreviewMediaRepository(private val media: Flow<List<MediaItem>>) : MediaRepository {
  override fun observeApprovedMedia(circleId: String): Flow<List<MediaItem>> = media

  override fun newMediaId(circleId: String): String = unused()

  override fun observeMediaShowing(circleId: String, shownId: String): Flow<List<MediaItem>> =
      unused()

  override fun observeCircleMedia(circleId: String): Flow<List<CircleMedia>> = unused()

  override suspend fun getMedia(circleId: String, mediaId: String): MediaItem? = unused()

  override suspend fun addMedia(circle: CareCircle, item: MediaItem, file: File?) = unused()

  override fun observeOutbox(circleId: String): Flow<List<OutboxEntry>> = unused()

  override suspend fun retrySend(circleId: String, mediaId: String) = unused()

  override suspend fun discardUnsent(circleId: String, mediaId: String) = unused()

  override suspend fun updateMedia(circleId: String, item: MediaItem) = unused()

  override suspend fun deleteMedia(circle: CareCircle, item: MediaItem) = unused()

  override suspend fun getFile(circle: CareCircle, item: MediaItem): File = unused()

  override suspend fun clearLocalMedia(circleId: String) = unused()

  override suspend fun clearLocalMedia() = unused()

  private fun unused(): Nothing =
      error("Not used by SeeAudios previews") // Used to avoid code repetition
}

private class PreviewProfiles(
    private val profile: UserProfile,
    private val showError: Boolean,
) : ProfileRepository {
  override suspend fun createProfile(person: Person, role: Role): Result<Unit> =
      error("Preview does not create profiles")

  override suspend fun updateOnboardingProfile(person: Person, role: Role): Result<Unit> =
      error("Preview does not update profiles")

  override suspend fun getCurrentProfile(): Result<UserProfile?> =
      if (showError) Result.failure(Exception("preview")) else Result.success(profile)

  override suspend fun getProfile(uid: String): Result<UserProfile?> = Result.success(profile)
}

private class PreviewCircles(private val circle: CareCircle) : CareCircleRepository {
  override fun newId(): String = circle.id

  override suspend fun getCircle(circleId: String): Result<CareCircle> = Result.success(circle)

  override suspend fun addPerson(circleId: String, person: Person): Result<Person> =
      error("Preview does not add people")
}

private class SeeAudiosPreviewOwner(state: PreviewAudioState) :
    ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
  override val viewModelStore = ViewModelStore()
  private val profile =
      UserProfile(
          uid = "preview",
          role = Role.PATIENT,
          person = Person(firstName = "Circee"),
          circleIds = listOf("preview"),
      )
  private val circle =
      CareCircle(id = "preview", people = mapOf("relative" to Person(firstName = "Evangeline")))

  private val profiles = PreviewProfiles(profile, state == PreviewAudioState.Error)
  private val circles = PreviewCircles(circle)

  private val media =
      PreviewMediaRepository(
          when (state) {
            PreviewAudioState.Loading -> flow { awaitCancellation() }
            PreviewAudioState.Content ->
                flowOf(
                    listOf(
                        MediaItem.Audio(
                            id = "sample1",
                            authorId = profile.uid,
                            description = "I have the best gossip...",
                            shownIds = listOf("relative"),
                            waveform = previewWaveform,
                        ),
                        MediaItem.Audio(
                            id = "sample2",
                            authorId = profile.uid,
                            description = "I saw otters at the zoo today!",
                            shownIds = listOf("relative"),
                            waveform = previewWaveform.asReversed(),
                        ),
                    )
                )
            PreviewAudioState.Empty,
            PreviewAudioState.Error -> flowOf(emptyList())
          }
      )

  private val previewPlayer =
      object : AudioPlayer {
        override val state: StateFlow<AudioState> = MutableStateFlow(AudioState())

        override suspend fun play(file: File) = Unit

        override fun pause() = Unit

        override fun resume() = Unit

        override fun seekTo(positionMs: Long) = Unit

        override fun stop(file: File) = Unit
      }
  private val audiosViewModel = SeeAudiosViewModel(media, circles, profiles, previewPlayer)

  override val defaultViewModelProviderFactory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            when (modelClass) {
              SeeAudiosViewModel::class.java -> audiosViewModel as T
              MediaCardViewModel::class.java -> MediaCardViewModel(profiles, circles) as T
              else -> error("Unexpected ViewModel: $modelClass")
            }
      }
}

@Composable
private fun PreviewSeeAudios(state: PreviewAudioState = PreviewAudioState.Empty) {
  val owner = remember(state) { SeeAudiosPreviewOwner(state) }
  CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
    AmberAppTheme { SeeAudiosScreen() }
  }
}

@Preview(showBackground = true, name = "Empty audios")
@Composable
fun SeeAudiosScreenPreview() {
  PreviewSeeAudios(PreviewAudioState.Empty)
}

@Preview(showBackground = true, name = "Loading audios")
@Composable
fun SeeAudiosLoadingPreview() {
  PreviewSeeAudios(PreviewAudioState.Loading)
}

@Preview(showBackground = true, name = "Audio error")
@Composable
fun SeeAudiosErrorPreview() {
  PreviewSeeAudios(PreviewAudioState.Error)
}

@Preview(showBackground = true, name = "Audios")
@Composable
fun SeeAudiosContentPreview() {
  PreviewSeeAudios(PreviewAudioState.Content)
}
