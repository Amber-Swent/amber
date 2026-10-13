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
 * @param createdRole role written on success, for a future navigation owner; null until then.
 */
data class CreateProfileUiState(
    val name: String = "",
    val surname: String = "",
    val nameError: Boolean = false,
    val surnameError: Boolean = false,
    val isSaving: Boolean = false,
    val submitError: String? = null,
    val createdRole: Role? = null,
)

/**
 * ViewModel for [CreateProfile]: validates name/surname and persists the profile on role choice.
 */
class CreateProfileViewModel(
    private val profileRepository: ProfileRepository = FirebaseProfileRepository(),
) : ViewModel() {

  private val _uiState = MutableStateFlow(CreateProfileUiState())
  val uiState: StateFlow<CreateProfileUiState> = _uiState.asStateFlow()

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
   * Does nothing while a save is already in progress. Does not call the repository when fields are
   * blank after trim.
   */
  fun onRoleSelected(role: Role) {
    if (_uiState.value.isSaving) return

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
              _uiState.value =
                  _uiState.value.copy(
                      isSaving = false,
                      submitError = null,
                      createdRole = role,
                  )
            }
            .onFailure { error ->
              _uiState.value =
                  _uiState.value.copy(
                      isSaving = false,
                      submitError = error.toSubmitErrorMessage(),
                      createdRole = null,
                  )
            }
      } catch (exception: CancellationException) {
        throw exception
      }
    }
  }

  private fun Throwable.toSubmitErrorMessage(): String = message ?: "Unable to create a profile"
}
