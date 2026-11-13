// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.
// Modified by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by MeiliV, with assistance from OpenAI Codex.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.navigation

import androidx.annotation.StringRes
import com.github.se.amber.R

/**
 * Base interface for all navigation destinations (Screen). Ensures consistent structure across all
 * user roles
 *
 * @property route The unique route used to identify the destination in navigation.
 * @property title The title of the destination, as a string resource.
 * @property isTopLevelDestination Whether the destination is a top-level destination.
 */
sealed interface Screen {
  val route: String
  @get:StringRes val title: Int
  val isTopLevelDestination: Boolean
}

/** Authentification navigation Screen */
sealed class AuthScreen(
    override val route: String,
    @param:StringRes override val title: Int,
    override val isTopLevelDestination: Boolean = false,
) : Screen {
  data object Auth :
      AuthScreen(
          route = "auth",
          title = R.string.screenTitleAuth,
      )
}

/** Intermediate screens that only redirect the user to another destination. */
sealed class TransitionScreen(
    override val route: String,
    @param:StringRes override val title: Int,
    override val isTopLevelDestination: Boolean = false,
) : Screen {
  data object RoleLoading :
      TransitionScreen(
          route = "role_loading",
          title = R.string.screenTitleRoleLoading,
      )
}

/** Screens of the onboarding flow, shown to a signed-in user who has no profile yet. */
sealed class OnboardingScreen(
    override val route: String,
    @param:StringRes override val title: Int,
    override val isTopLevelDestination: Boolean = false,
) : Screen {

  data object CreateProfile :
      OnboardingScreen(
          route = "create_profile",
          title = R.string.screenTitleCreateProfile,
      )

  data object CaregiverChoice :
      OnboardingScreen(
          route = "caregiver_choice",
          title = R.string.screenTitleCaregiverChoice,
      )

  data object CreateCareCircle :
      OnboardingScreen(
          route = "create_carecircle",
          title = R.string.screenTitleCreateCareCircle,
      )

  data object EnterInvitation :
      OnboardingScreen(
          route = "enter_invitation",
          title = R.string.screenTitleEnterInvitation,
      )
}

/**
 * Caregiver's navigation Screen.
 *
 * See Figma for more information on each Screen.
 */
sealed class CaregiverScreen(
    override val route: String,
    @param:StringRes override val title: Int,
    override val isTopLevelDestination: Boolean = false,
) : Screen {
  data object Home :
      CaregiverScreen(
          route = "caregiver_home",
          title = R.string.screenTitleCaregiverHome,
          isTopLevelDestination = true,
      )

  data object Upload :
      CaregiverScreen(
          route = "caregiver_upload",
          title = R.string.screenTitleCaregiverUpload,
          isTopLevelDestination = true,
      )

  data object UploadPicture :
      CaregiverScreen(
          route = "caregiver_upload_picture",
          title = R.string.screenTitleCaregiverUploadPicture,
      )

  data object UploadAudio :
      CaregiverScreen(
          route = "caregiver_upload_audio",
          title = R.string.screenTitleCaregiverUploadAudio,
      )

  data object Parameters :
      CaregiverScreen(
          route = "parameters",
          title = R.string.screenTitleCaregiverParameters,
          isTopLevelDestination = true,
      )

  data object SignOut :
      CaregiverScreen(
          route = "caregiver_sign_out",
          title = R.string.screenTitleCaregiverSignOut,
      )

  data object DeleteAccount :
      CaregiverScreen(
          route = "caregiver_delete_account",
          title = R.string.screenTitleCaregiverDeleteAccount,
      )

  data object CreateInvitationCode :
      CaregiverScreen(
          route = "create_invitation_code",
          title = R.string.screenTitleCaregiverCreateInvitationCode,
      )
}

/**
 * Patient's navigation destinations.
 *
 * See Figma for more information on the design.
 */
sealed class PatientScreen(
    override val route: String,
    @param:StringRes override val title: Int,
    override val isTopLevelDestination: Boolean = false,
) : Screen {
  data object Home :
      PatientScreen(
          route = "patient_home",
          title = R.string.screenTitlePatientHome,
          isTopLevelDestination = true,
      )

  data object SeeMedias :
      PatientScreen(
          route = "patient_see_medias",
          title = R.string.screenTitlePatientSeeMedias,
          isTopLevelDestination = true,
      )

  data object SeePictures :
      PatientScreen(
          route = "patient_see_pictures",
          title = R.string.screenTitlePatientSeePictures,
      )

  data object SeeAudios :
      PatientScreen(
          route = "patient_see_audios",
          title = R.string.screenTitlePatientSeeAudios,
      )

  data object Parameters :
      PatientScreen(
          route = "patient_parameters",
          title = R.string.screenTitlePatientParameters,
          isTopLevelDestination = true,
      )

  data object SignOut :
      PatientScreen(
          route = "patient_sign_out",
          title = R.string.screenTitlePatientSignOut,
      )

  data object DeleteAccount :
      PatientScreen(
          route = "patient_delete_account",
          title = R.string.screenTitleCaregiverDeleteAccount,
      )
}

/** Routes of the nested navigation graphs, one per user role. */
object NavGraphs {
  const val CAREGIVER = "caregiver_graph"
  const val PATIENT = "patient_graph"
  const val AUTH = "auth_graph"
  const val ONBOARDING = "onboarding_graph"
}
