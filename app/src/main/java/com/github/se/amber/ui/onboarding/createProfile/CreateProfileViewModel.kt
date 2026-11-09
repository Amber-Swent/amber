// Written by Alaabrine with the aid of an AI coding agent
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.onboarding.createProfile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.data.profile.ProfileCreationError
import com.github.se.amber.data.profile.ProfileReadError
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.data.profile.ProfileUpdateError
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.validation.InputValidation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val FILL_OUT_MESSAGE = "Please fill this out"

/**
 * UI state for the create-profile step.
 *
 * Field errors are derived from the value: a name with characters a name can't have shows its error
 * at once, while a blank one only does once validation has been attempted, matching the bootcamp B2
 * pattern. [submitError] carries mapped repository failures.
 *
 * @param name first name entered by the user.
 * @param surname last name entered by the user.
 * @param validationAttempted true after the user has pressed Patient or Caregiver at least once.
 * @param isSaving true while a create-profile request is in flight.
 * @param submitError actionable repository failure message, or null when there is none.
 */
data class CreateProfileUIState(
    val name: String = "",
    val surname: String = "",
    val validationAttempted: Boolean = false,
    val isSaving: Boolean = false,
    val submitError: String? = null,
) {
  /** Why [name] is refused, or null; a blank or unfinished one only once validation ran. */
  val nameError: String?
    get() = errorOf(name)

  /** Why [surname] is refused, or null; a blank or unfinished one only once validation ran. */
  val surnameError: String?
    get() = errorOf(surname)

  /** Whether the role buttons can be pressed: no save in progress and no error shown. */
  val canSubmit: Boolean
    get() = !isSaving && nameError == null && surnameError == null

  private fun errorOf(value: String): String? =
      InputValidation.NAME.errorMessage(
          value,
          submitted = validationAttempted,
          blankMessage = FILL_OUT_MESSAGE,
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
          submitError = null,
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
      _uiState.update { it.copy(isSaving = true, submitError = null) }
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
    _uiState.update { it.copy(isSaving = false, submitError = null) }
    onSuccess(role)
  }

  private fun showFailure(error: Throwable) {
    _uiState.update { it.copy(isSaving = false, submitError = error.toActionableMessage()) }
  }

  private fun updateNameField(name: String? = null, surname: String? = null) {
    _uiState.update {
      it.copy(
          name = name ?: it.name,
          surname = surname ?: it.surname,
          submitError = null,
      )
    }
  }
}

private data class SavedOnboardingProfile(
    val name: String,
    val surname: String,
    val role: Role,
)

private fun Throwable.toActionableMessage(): String =
    when (this) {
      is ProfileCreationError.InvalidPersonDetails,
      is ProfileUpdateError.InvalidPersonDetails -> "Enter your first and last name and try again."
      is ProfileCreationError.NotAuthenticated,
      is ProfileUpdateError.NotAuthenticated,
      is ProfileReadError.NotAuthenticated -> "Your session expired. Sign in and try again."
      is ProfileCreationError.ProfileAlreadyExists,
      is ProfileUpdateError.AlreadyInCareCircle -> "A profile already exists for this account."
      is ProfileCreationError.PermissionDenied,
      is ProfileUpdateError.PermissionDenied,
      is ProfileReadError.PermissionDenied -> "You don't have permission to create a profile."
      is ProfileCreationError.Network,
      is ProfileUpdateError.Network,
      is ProfileReadError.Network -> "You're offline. Check your connection and try again."
      else -> "Couldn't create your profile. Please try again."
    }
