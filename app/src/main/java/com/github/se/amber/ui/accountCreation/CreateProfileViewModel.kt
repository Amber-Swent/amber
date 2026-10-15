// this code was written by Alaabrine
// with the aid of an AI coding agent
package com.github.se.amber.ui.accountCreation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.data.profile.FirebaseProfileRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the create-profile step.
 *
 * @param name first name entered by the user.
 * @param surname last name entered by the user.
 * @param nameError whether the name field should show a fill-out error.
 * @param surnameError whether the surname field should show a fill-out error.
 * @param isSaving true while a create-profile request is in flight.
 * @param submitError repository failure message, or null when there is none.
 */
data class CreateProfileUiState(
    val name: String = "",
    val surname: String = "",
    val nameError: Boolean = false,
    val surnameError: Boolean = false,
    val isSaving: Boolean = false,
    val submitError: String? = null,
)

/**
 * ViewModel for [CreateProfileScreen]: validates name/surname and persists the profile on role
 * choice.
 *
 * @param onSuccess invoked with the chosen [Role] and trimmed first name after a successful create,
 *   so the navigation owner can move to the next onboarding step without observing UI state.
 */
class CreateProfileViewModel(
    private val profileRepository: ProfileRepository = FirebaseProfileRepository(),
    private val onSuccess: (role: Role, displayName: String) -> Unit = { _, _ -> },
) : ViewModel() {

  private val _uiState = MutableStateFlow(CreateProfileUiState())
  val uiState: StateFlow<CreateProfileUiState> = _uiState.asStateFlow()

  private var hasCreatedProfile = false

  /**
   * Updates [CreateProfileUiState.name] and clears [CreateProfileUiState.nameError] when non-blank.
   */
  fun onNameChange(value: String) {
    _uiState.value =
        _uiState.value.copy(
            name = value,
            nameError = value.trim().isEmpty() && _uiState.value.nameError,
        )
  }

  /**
   * Updates [CreateProfileUiState.surname] and clears [CreateProfileUiState.surnameError] when
   * non-blank.
   */
  fun onSurnameChange(value: String) {
    _uiState.value =
        _uiState.value.copy(
            surname = value,
            surnameError = value.trim().isEmpty() && _uiState.value.surnameError,
        )
  }

  /**
   * Validates name and surname, then creates the profile with [role] via [ProfileRepository].
   *
   * Does nothing while a save is already in progress or after a successful create. Does not call
   * the repository when fields are blank after trim.
   */
  fun onRoleSelected(role: Role) {
    if (_uiState.value.isSaving || hasCreatedProfile) return

    val trimmedName = _uiState.value.name.trim()
    val trimmedSurname = _uiState.value.surname.trim()
    val nameBlank = trimmedName.isEmpty()
    val surnameBlank = trimmedSurname.isEmpty()

    _uiState.value =
        _uiState.value.copy(
            name = trimmedName,
            surname = trimmedSurname,
            nameError = nameBlank,
            surnameError = surnameBlank,
            submitError = null,
        )

    if (nameBlank || surnameBlank) return

    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isSaving = true, submitError = null)
      try {
        val result =
            profileRepository.createProfile(
                person = Person(firstName = trimmedName, lastName = trimmedSurname),
                role = role,
            )
        result
            .onSuccess {
              hasCreatedProfile = true
              _uiState.value =
                  _uiState.value.copy(
                      isSaving = false,
                      submitError = null,
                  )
              onSuccess(role, trimmedName)
            }
            .onFailure { error ->
              _uiState.value =
                  _uiState.value.copy(
                      isSaving = false,
                      submitError = error.toSubmitErrorMessage(),
                  )
            }
      } catch (exception: CancellationException) {
        throw exception
      }
    }
  }

  private fun Throwable.toSubmitErrorMessage(): String = message ?: "Unable to create a profile"
}
