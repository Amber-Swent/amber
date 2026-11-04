// Written by Alaabrine, with assistance from Cursor.
package com.github.se.amber.ui.caregiver.upload.pending

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.circle.CareCircleRepositoryProvider
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MediaRepositoryProvider
import com.github.se.amber.model.media.OutboxEntry
import com.github.se.amber.model.media.SendState
import com.github.se.amber.ui.offline.toLoadErrorMessage
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the pending-uploads screen shows.
 *
 * @param isLoading Whether the first list has not arrived yet.
 * @param circle The care circle the entries belong to, or a circle that only has its id when the
 *   circle document could not be loaded. Pictures are shown from the copy still on the phone.
 * @param entries Media saved on this phone and not sent yet.
 * @param errorMsg Message to display, or null when there is none.
 * @param discardMediaId The failed upload the user is confirming they want to discard.
 */
data class PendingUploadsUIState(
    val isLoading: Boolean = true,
    val circle: CareCircle? = null,
    val entries: List<OutboxEntry> = emptyList(),
    val errorMsg: String? = null,
    val discardMediaId: String? = null,
)

/**
 * Lists the caregiver's unsent media and lets them retry or discard a failed send.
 *
 * @param profileRepository Loads the signed-in caregiver, whose first circle id selects the outbox.
 * @param circleRepository Loads that circle when it is available. The list still shows if it is
 *   not.
 * @param mediaRepository The outbox, and retry and discard.
 */
class PendingUploadsViewModel(
    private val profileRepository: ProfileRepository = ProfileRepositoryProvider.default.repository,
    private val circleRepository: CareCircleRepository =
        CareCircleRepositoryProvider.default.repository,
    private val mediaRepository: MediaRepository = MediaRepositoryProvider.repository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(PendingUploadsUIState())
  val uiState: StateFlow<PendingUploadsUIState> = _uiState.asStateFlow()

  init {
    load()
  }

  /** Clears the error message from the UI state. */
  fun clearErrorMsg() {
    _uiState.update { it.copy(errorMsg = null) }
  }

  /** Sends [mediaId] again when its upload has failed. */
  fun retry(mediaId: String) {
    val circleId = _uiState.value.circle?.id ?: return
    if (entry(mediaId)?.state != SendState.FAILED) return
    viewModelScope.launch {
      try {
        mediaRepository.retrySend(circleId, mediaId)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Could not retry $mediaId", e)
        _uiState.update { it.copy(errorMsg = COULD_NOT_RETRY) }
      }
    }
  }

  /** Asks the user to confirm discarding [mediaId]. A send that has not failed is left alone. */
  fun requestDiscard(mediaId: String) {
    if (entry(mediaId)?.state != SendState.FAILED) return
    _uiState.update { it.copy(discardMediaId = mediaId) }
  }

  /** Closes the discard confirmation without deleting anything. */
  fun dismissDiscard() {
    _uiState.update { it.copy(discardMediaId = null) }
  }

  /** Deletes the upload the user confirmed, when it is still failed. */
  fun confirmDiscard() {
    val mediaId = _uiState.value.discardMediaId ?: return
    val circleId = _uiState.value.circle?.id
    _uiState.update { it.copy(discardMediaId = null) }
    if (circleId == null || entry(mediaId)?.state != SendState.FAILED) return
    viewModelScope.launch {
      try {
        mediaRepository.discardUnsent(circleId, mediaId)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Could not discard $mediaId", e)
        _uiState.update { it.copy(errorMsg = COULD_NOT_DISCARD) }
      }
    }
  }

  private fun entry(mediaId: String): OutboxEntry? =
      _uiState.value.entries.find { it.item.id == mediaId }

  private fun load() {
    viewModelScope.launch {
      val profile =
          profileRepository.getCurrentProfile().getOrElse { error ->
            Log.e(TAG, "Error fetching profile", error)
            showLoadError(
                error.toLoadErrorMessage(
                    "Failed to load the profile: ${error.message}",
                    showingSaved = false,
                )
            )
            return@launch
          }
              ?: run {
                showLoadError("No profile found.")
                return@launch
              }

      val circleId = profile.circleIds.firstOrNull { it.isNotBlank() }
      if (circleId == null) {
        showLoadError("No care circle found for this caregiver.")
        return@launch
      }

      val loadedCircle = circleRepository.getCircle(circleId)
      val circle = loadedCircle.getOrDefault(CareCircle(id = circleId))
      val circleError =
          loadedCircle.exceptionOrNull()?.let { error ->
            Log.e(TAG, "Error fetching care circle", error)
            error.toLoadErrorMessage(
                "Failed to load the care circle: ${error.message}",
                showingSaved = true,
            )
          }
      _uiState.update { it.copy(isLoading = false, circle = circle, errorMsg = circleError) }

      mediaRepository
          .observeOutbox(circleId)
          .catch { error ->
            Log.e(TAG, "Error observing pending uploads", error)
            _uiState.update {
              it.copy(
                  isLoading = false,
                  errorMsg =
                      error.toLoadErrorMessage(
                          "Failed to load pending uploads: ${error.message}",
                          showingSaved = it.entries.isNotEmpty(),
                      ),
              )
            }
          }
          .collect { entries ->
            _uiState.update { state ->
              val discardId = state.discardMediaId
              val discardStillFailed = entries.any {
                it.item.id == discardId && it.state == SendState.FAILED
              }
              state.copy(
                  isLoading = false,
                  entries = entries,
                  discardMediaId = discardId.takeIf { discardStillFailed },
              )
            }
          }
    }
  }

  private fun showLoadError(message: String) {
    _uiState.value = PendingUploadsUIState(isLoading = false, errorMsg = message)
  }

  private companion object {
    const val TAG = "PendingUploadsViewModel"
    const val COULD_NOT_RETRY = "Could not retry this upload."
    const val COULD_NOT_DISCARD = "Could not discard this upload."
  }
}
