// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.caregiver.parameters.changeCareCircle

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.circle.ActiveCircleRepositoryProvider
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.circle.CareCircleRepositoryProvider
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A care circle available to the caregiver.
 *
 * @param id Stable ID used to identify and select the circle.
 * @param name Name displayed on the chip and as the current circle.
 */
data class ChangeCareCircleChoice(val id: String, val name: String)

/**
 * Determines which content [ChangeCareCircleScreen] renders.
 *
 * [LOADING] shows progress, [EMPTY] shows the no-circles message, [READY] shows the circles, and
 * [ERROR] shows a loading failure.
 */
enum class ChangeCareCircleStatus {
  LOADING,
  EMPTY,
  READY,
  ERROR,
}

/**
 * State displayed by [ChangeCareCircleScreen].
 *
 * The screen uses [status] to choose the visible section. In [ChangeCareCircleStatus.READY], it
 * looks up [activeCircleId] in [circles] for the current-circle name and selected chip. A null or
 * unmatched ID displays the localized fallback and leaves every chip unselected.
 *
 * The default status is [ChangeCareCircleStatus.LOADING].
 *
 * @param status Determines whether loading, empty, or ready content is shown.
 * @param circles Care circles displayed as chips in the ready state.
 * @param activeCircleId ID of the selected circle, or null if none is active.
 * @param errorMsgRes Error shown as a blocking message after loading fails, or as a toast when
 *   changing the active circle fails in the ready state.
 */
data class ChangeCareCircleUIState(
    val status: ChangeCareCircleStatus = ChangeCareCircleStatus.LOADING,
    val circles: List<ChangeCareCircleChoice> = emptyList(),
    val activeCircleId: String? = null,
    @param:StringRes val errorMsgRes: Int? = null,
)

/**
 * Owns the state and selection actions for the change care circle screen.
 *
 * Loads the signed-in caregiver's circles and observes the device's active-circle choice. A
 * supplied [initialState] is used only by previews and screen tests and skips automatic loading.
 *
 * @param initialState Optional preview state.
 */
class ChangeCareCircleViewModel(
    initialState: ChangeCareCircleUIState? = null,
    private val profileRepository: ProfileRepository = ProfileRepositoryProvider.default.repository,
    private val circleRepository: CareCircleRepository =
        CareCircleRepositoryProvider.default.repository,
    private val activeCircleRepository: ActiveCircleRepository =
        ActiveCircleRepositoryProvider.default.repository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(initialState ?: ChangeCareCircleUIState())
  val uiState: StateFlow<ChangeCareCircleUIState> = _uiState.asStateFlow()
  private var loadJob: Job? = null
  private var selectionJob: Job? = null
  private var caregiverUid: String? = null

  /** Clears the selection error after its toast has been shown. */
  fun clearErrorMsg() {
    _uiState.update { current ->
      if (current.status == ChangeCareCircleStatus.READY) current.copy(errorMsgRes = null)
      else current
    }
  }

  /**
   * Records a non-blocking selection error for the screen to show as a toast.
   *
   * The ready screen and its current circle remain visible. This method has no effect after the
   * screen leaves the ready state.
   *
   * @param errorMsgRes Localized string resource for the toast.
   */
  private fun setErrorMsg(@StringRes errorMsgRes: Int) {
    _uiState.update { current ->
      if (current.status == ChangeCareCircleStatus.READY) current.copy(errorMsgRes = errorMsgRes)
      else current
    }
  }

  /**
   * Replaces the screen with a loading error and retry action.
   *
   * @param errorMsgRes Localized string resource displayed on the error screen.
   */
  private fun setBlockingErrorMsg(@StringRes errorMsgRes: Int) {
    _uiState.value =
        ChangeCareCircleUIState(
            status = ChangeCareCircleStatus.ERROR,
            errorMsgRes = errorMsgRes,
        )
  }

  init {
    if (initialState == null) loadCareCircles()
  }

  /**
   * Load the caregiver's circles and active circle into [uiState].
   *
   * Reloads the profile and every circle in its membership list. The active-circle flow then keeps
   * the selected chip in sync with changes made elsewhere in the app.
   */
  fun loadCareCircles() {
    loadJob?.cancel()
    selectionJob?.cancel()
    caregiverUid = null
    _uiState.value = ChangeCareCircleUIState()
    loadJob = viewModelScope.launch {
      try {
        val profile =
            profileRepository.getCurrentProfile().getOrElse { error ->
              Log.e(TAG, "Error fetching profile", error)
              setBlockingErrorMsg(R.string.changeCareCircleErrorLoadProfile)
              return@launch
            }
        if (profile == null || profile.uid.isBlank()) {
          setBlockingErrorMsg(R.string.changeCareCircleErrorLoadProfile)
          return@launch
        }
        val ids = profile.circleIds.filter(String::isNotBlank).distinct()
        if (ids.isEmpty()) {
          _uiState.value = ChangeCareCircleUIState(status = ChangeCareCircleStatus.EMPTY)
          return@launch
        }
        val circles = ids.map { id ->
          val circle =
              circleRepository.getCircle(id).getOrElse { error ->
                Log.e(TAG, "Error fetching care circle", error)
                setBlockingErrorMsg(R.string.changeCareCircleErrorLoadCircle)
                return@launch
              }
          ChangeCareCircleChoice(id = id, name = circle.name)
        }
        caregiverUid = profile.uid
        activeCircleRepository.activeCircleId(profile.uid, ids).collect { activeId ->
          _uiState.update { current ->
            current.copy(
                status = ChangeCareCircleStatus.READY,
                circles = circles,
                activeCircleId = activeId,
            )
          }
        }
      } catch (error: CancellationException) {
        throw error
      } catch (error: Exception) {
        Log.e(TAG, "Error loading care circles", error)
        setBlockingErrorMsg(R.string.changeCareCircleErrorLoadCircle)
      }
    }
  }

  /**
   * Persists a valid circle selection for the current caregiver.
   *
   * Ignores selections while loading or saving, as well as unknown or already selected circles. A
   * successful write updates [uiState]. On failure, the previously selected circle stays visible
   * and [setErrorMsg] requests a toast so the caregiver can try again.
   *
   * @param circleId ID of the circle selected by the caregiver.
   */
  fun changeActiveCareCircle(circleId: String) {
    val state = _uiState.value
    val uid = caregiverUid ?: return

    if (
        state.status != ChangeCareCircleStatus.READY ||
            circleId == state.activeCircleId ||
            state.circles.none { it.id == circleId } ||
            selectionJob?.isActive == true
    )
        return
    selectionJob = viewModelScope.launch {
      try {
        activeCircleRepository.setActiveCircle(uid, circleId)
        _uiState.update { current ->
          if (
              current.status == ChangeCareCircleStatus.READY &&
                  current.circles.any { it.id == circleId }
          )
              current.copy(activeCircleId = circleId)
          else current
        }
      } catch (error: CancellationException) {
        throw error
      } catch (error: Exception) {
        Log.e(TAG, "Error changing active care circle", error)
        setErrorMsg(R.string.changeCareCircleErrorChange)
      }
    }
  }

  private companion object {
    const val TAG = "ChangeCareCircleViewModel"
  }
}
