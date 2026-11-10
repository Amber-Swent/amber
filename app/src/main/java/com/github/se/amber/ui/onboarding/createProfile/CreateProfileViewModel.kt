// Written by Alaabrine with the aid of an AI coding agent
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.onboarding.createProfile

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
import com.github.se.amber.data.profile.ProfileCreationError
import com.github.se.amber.data.profile.ProfileReadError
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.data.profile.ProfileUpdateError
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

  private var savedProfile: SavedOnboardingProfile? = null

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
   * Validates name and surname, then saves the profile with [role].
   *
   * The first successful choice creates the profile. Pressing the same role again only navigates.
   * Pressing the other role updates the stored onboarding profile, so a wrong choice can be
   * corrected after Cancel. If this screen is recreated and the profile already exists with no care
   * circle, the selected role is written over that profile instead of failing with "already
   * exists".
   *
   * [onSuccess] runs after the profile matches [role], so the navigation owner can move forward
   * without observing UI state. Does nothing while a save is already in progress. Does not call the
   * repository when a field is blank after trim or isn't a valid name.
   */
  fun onRoleSelected(role: Role, onSuccess: (Role) -> Unit = {}) {
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

    val saved = savedProfile
    if (
        saved != null &&
            saved.name == trimmedName &&
            saved.surname == trimmedSurname &&
            saved.role == role
    ) {
      onSuccess(role)
      return
    }

    viewModelScope.launch {
      _uiState.update { it.copy(isSaving = true, errorMsgRes = null) }
      val person = Person(firstName = trimmedName, lastName = trimmedSurname)
      val result =
          if (saved == null) {
            profileRepository.createProfile(person = person, role = role)
          } else {
            profileRepository.updateOnboardingProfile(person = person, role = role)
          }
      result.fold(
          onSuccess = { rememberAndContinue(trimmedName, trimmedSurname, role, onSuccess) },
          onFailure = { error ->
            if (saved == null && error is ProfileCreationError.ProfileAlreadyExists) {
              correctExistingProfile(person, role, onSuccess)
            } else {
              showFailure(error)
            }
          },
      )
    }
  }

  private suspend fun correctExistingProfile(
      person: Person,
      role: Role,
      onSuccess: (Role) -> Unit,
  ) {
    val current =
        profileRepository.getCurrentProfile().getOrElse { error ->
          showFailure(error)
          return
        }
    val alreadyJoined = current?.circleIds?.any { it.isNotBlank() } == true
    if (current == null || alreadyJoined) {
      showFailure(ProfileCreationError.ProfileAlreadyExists)
      return
    }
    if (
        current.role == role &&
            current.person.firstName == person.firstName &&
            current.person.lastName == person.lastName
    ) {
      rememberAndContinue(person.firstName, person.lastName, role, onSuccess)
      return
    }
    profileRepository
        .updateOnboardingProfile(person = person, role = role)
        .fold(
            onSuccess = { rememberAndContinue(person.firstName, person.lastName, role, onSuccess) },
            onFailure = { showFailure(it) },
        )
  }

  private fun rememberAndContinue(
      name: String,
      surname: String,
      role: Role,
      onSuccess: (Role) -> Unit,
  ) {
    savedProfile = SavedOnboardingProfile(name = name, surname = surname, role = role)
    _uiState.update { it.copy(isSaving = false, errorMsgRes = null) }
    onSuccess(role)
  }

  private fun showFailure(error: Throwable) {
    _uiState.update { it.copy(isSaving = false, errorMsgRes = error.errorMessage()) }
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

private data class SavedOnboardingProfile(
    val name: String,
    val surname: String,
    val role: Role,
)

/** Maps a repository failure to the string resource of the message shown to the user. */
@StringRes
private fun Throwable.errorMessage(): Int =
    when (this) {
      is ProfileCreationError.InvalidPersonDetails,
      is ProfileUpdateError.InvalidPersonDetails -> R.string.createProfileErrorInvalidName
      is ProfileCreationError.NotAuthenticated,
      is ProfileUpdateError.NotAuthenticated,
      is ProfileReadError.NotAuthenticated -> R.string.createProfileErrorSessionExpired
      is ProfileCreationError.ProfileAlreadyExists -> R.string.createProfileErrorExists
      is ProfileUpdateError.AlreadyInCareCircle -> R.string.createProfileErrorAlreadyInCircle
      is ProfileCreationError.PermissionDenied,
      is ProfileUpdateError.PermissionDenied,
      is ProfileReadError.PermissionDenied -> R.string.createProfileErrorPermissionDenied
      is ProfileCreationError.Network,
      is ProfileUpdateError.Network,
      is ProfileReadError.Network -> R.string.createProfileErrorOffline
      else -> R.string.createProfileErrorUnknown
    }
