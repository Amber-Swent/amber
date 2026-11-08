// Written by Alaabrine with the aid of an AI coding agent
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.onboarding.createProfile

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
import com.github.se.amber.data.profile.ProfileCreationError
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the create-profile step.
 *
 * Field errors are derived from the value once validation has been attempted (blank + already
 * touched), matching the bootcamp B2 pattern. [submitErrorRes] carries mapped repository failures.
 * Errors are string resources, resolved by the screen in the app language.
 *
 * @param name first name entered by the user.
 * @param surname last name entered by the user.
 * @param validationAttempted true after the user has pressed Patient or Caregiver at least once.
 * @param isSaving true while a create-profile request is in flight.
 * @param submitErrorRes actionable repository failure message, or null when there is none.
 */
data class CreateProfileUIState(
    val name: String = "",
    val surname: String = "",
    val validationAttempted: Boolean = false,
    val isSaving: Boolean = false,
    @param:StringRes val submitErrorRes: Int? = null,
) {
  /** Fill-out message when validation ran and [name] is blank; otherwise null. */
  @get:StringRes
  val nameErrorRes: Int?
    get() = if (validationAttempted && name.isBlank()) R.string.createProfileFillOut else null

  /** Fill-out message when validation ran and [surname] is blank; otherwise null. */
  @get:StringRes
  val surnameErrorRes: Int?
    get() = if (validationAttempted && surname.isBlank()) R.string.createProfileFillOut else null
}

/**
 * ViewModel for [CreateProfileScreen]: validates name/surname and persists the profile on role
 * choice.
 *
 * @param profileRepository source of profile create/read operations.
 */
class CreateProfileViewModel(
    private val profileRepository: ProfileRepository = ProfileRepositoryProvider.default.repository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(CreateProfileUIState())
  val uiState: StateFlow<CreateProfileUIState> = _uiState.asStateFlow()

  private var createdRole: Role? = null

  /** Updates [CreateProfileUIState.name]; field error clears automatically when non-blank. */
  fun onNameChange(value: String) {
    updateNameField(name = value)
  }

  /** Updates [CreateProfileUIState.surname]; field error clears automatically when non-blank. */
  fun onSurnameChange(value: String) {
    updateNameField(surname = value)
  }

  /**
   * Validates name and surname, then creates the profile with [role] via [ProfileRepository].
   *
   * [onSuccess] is invoked after a successful create (and again if the user returns and presses a
   * role button), so the navigation owner can move forward without observing UI state. Does nothing
   * while a save is already in progress. Does not call the repository when fields are blank after
   * trim.
   */
  fun onRoleSelected(role: Role, onSuccess: (Role) -> Unit = {}) {
    createdRole?.let {
      onSuccess(it)
      return
    }
    if (_uiState.value.isSaving) return

    val trimmedName = _uiState.value.name.trim()
    val trimmedSurname = _uiState.value.surname.trim()

    _uiState.update {
      it.copy(
          name = trimmedName,
          surname = trimmedSurname,
          validationAttempted = true,
          submitErrorRes = null,
      )
    }

    if (trimmedName.isBlank() || trimmedSurname.isBlank()) return

    viewModelScope.launch {
      _uiState.update { it.copy(isSaving = true, submitErrorRes = null) }
      val result =
          profileRepository.createProfile(
              person = Person(firstName = trimmedName, lastName = trimmedSurname),
              role = role,
          )
      result
          .onSuccess {
            createdRole = role
            _uiState.update { it.copy(isSaving = false, submitErrorRes = null) }
            onSuccess(role)
          }
          .onFailure { error ->
            _uiState.update {
              it.copy(
                  isSaving = false,
                  submitErrorRes = error.errorMessage(),
              )
            }
          }
    }
  }

  private fun updateNameField(name: String? = null, surname: String? = null) {
    _uiState.update {
      it.copy(
          name = name ?: it.name,
          surname = surname ?: it.surname,
          submitErrorRes = null,
      )
    }
  }
}

/** Maps a repository failure to the string resource of the message shown to the user. */
@StringRes
private fun Throwable.errorMessage(): Int =
    when (this) {
      is ProfileCreationError.InvalidPersonDetails -> R.string.createProfileErrorInvalidName
      is ProfileCreationError.NotAuthenticated -> R.string.createProfileErrorSessionExpired
      is ProfileCreationError.ProfileAlreadyExists -> R.string.createProfileErrorExists
      is ProfileCreationError.PermissionDenied -> R.string.createProfileErrorPermissionDenied
      is ProfileCreationError.Network -> R.string.createProfileErrorOffline
      else -> R.string.createProfileErrorUnknown
    }
