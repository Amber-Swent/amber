// Written by Alaabrine with the aid of an AI coding agent
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

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
import com.github.se.amber.model.validation.InputValidation
import com.github.se.amber.ui.utils.UiText
import com.github.se.amber.ui.utils.errorText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the create-profile step.
 *
 * Field errors are derived from the value: a name with characters a name can't have shows its error
 * at once, while a blank one only does once validation has been attempted, matching the bootcamp B2
 * pattern. [errorMsgRes] carries mapped repository failures.
 *
 * @param name first name entered by the user.
 * @param surname last name entered by the user.
 * @param validationAttempted true after the user has pressed Patient or Caregiver at least once.
 * @param isSaving true while a create-profile request is in flight.
 * @param errorMsgRes actionable repository failure message, or null when there is none.
 */
data class CreateProfileUIState(
    val name: String = "",
    val surname: String = "",
    val validationAttempted: Boolean = false,
    val isSaving: Boolean = false,
    @param:StringRes val errorMsgRes: Int? = null,
) {
  /** Why [name] is refused, or null; a blank or unfinished one only once validation ran. */
  val nameError: UiText?
    get() = errorOf(name)

  /** Why [surname] is refused, or null; a blank or unfinished one only once validation ran. */
  val surnameError: UiText?
    get() = errorOf(surname)

  /** Whether the role buttons can be pressed: no save in progress and no error shown. */
  val canSubmit: Boolean
    get() = !isSaving && nameError == null && surnameError == null

  private fun errorOf(value: String): UiText? =
      InputValidation.NAME.errorText(
          value,
          submitted = validationAttempted,
          blankRes = R.string.createProfileFillOut,
          formatRes = R.string.inputErrorNameFormat,
      )
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

  /**
   * Updates [CreateProfileUIState.name], cut at [InputValidation.NAME_MAX_LENGTH]; field error
   * clears automatically once the name is valid.
   */
  fun onNameChange(value: String) {
    updateNameField(name = InputValidation.NAME.clean(value))
  }

  /**
   * Updates [CreateProfileUIState.surname], cut at [InputValidation.NAME_MAX_LENGTH]; field error
   * clears automatically once the surname is valid.
   */
  fun onSurnameChange(value: String) {
    updateNameField(surname = InputValidation.NAME.clean(value))
  }

  /**
   * Validates name and surname, then creates the profile with [role] via [ProfileRepository].
   *
   * [onSuccess] is invoked after a successful create (and again if the user returns and presses a
   * role button), so the navigation owner can move forward without observing UI state. Does nothing
   * while a save is already in progress. Does not call the repository when a field is blank after
   * trim or isn't a valid name.
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
          errorMsgRes = null,
      )
    }

    if (
        !InputValidation.NAME.isValid(trimmedName) || !InputValidation.NAME.isValid(trimmedSurname)
    ) {
      return
    }

    viewModelScope.launch {
      _uiState.update { it.copy(isSaving = true, errorMsgRes = null) }
      val result =
          profileRepository.createProfile(
              person = Person(firstName = trimmedName, lastName = trimmedSurname),
              role = role,
          )
      result
          .onSuccess {
            createdRole = role
            _uiState.update { it.copy(isSaving = false, errorMsgRes = null) }
            onSuccess(role)
          }
          .onFailure { error ->
            _uiState.update {
              it.copy(
                  isSaving = false,
                  errorMsgRes = error.errorMessage(),
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
          errorMsgRes = null,
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
