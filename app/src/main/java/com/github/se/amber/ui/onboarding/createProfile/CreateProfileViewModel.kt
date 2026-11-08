// Written by Alaabrine with the aid of an AI coding agent
// Modified by Yuhan YANG, with assistance from Cursor.

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val FILL_OUT_MESSAGE = "Please fill this out"

/**
 * UI state for the create-profile step.
 *
 * Field errors are derived from the value once validation has been attempted (blank + already
 * touched), matching the bootcamp B2 pattern. [submitError] carries mapped repository failures.
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
  /** Fill-out message when validation ran and [name] is blank; otherwise null. */
  val nameError: String?
    get() = if (validationAttempted && name.isBlank()) FILL_OUT_MESSAGE else null

  /** Fill-out message when validation ran and [surname] is blank; otherwise null. */
  val surnameError: String?
    get() = if (validationAttempted && surname.isBlank()) FILL_OUT_MESSAGE else null
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

  /** Updates [CreateProfileUIState.name]; field error clears automatically when non-blank. */
  fun onNameChange(value: String) {
    updateNameField(name = value)
  }

  /** Updates [CreateProfileUIState.surname]; field error clears automatically when non-blank. */
  fun onSurnameChange(value: String) {
    updateNameField(surname = value)
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
   * repository when fields are blank after trim.
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

    if (trimmedName.isBlank() || trimmedSurname.isBlank()) return

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
