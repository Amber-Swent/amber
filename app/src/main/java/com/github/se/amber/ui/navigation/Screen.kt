package com.github.se.amber.ui.navigation

/**
 * Base interface for all navigation destinations (Screen). Ensures consistent structure across all
 * user roles
 *
 * @property route The unique route used to identify the destination in navigation.
 * @property name The title associated with the destination.
 * @property isTopLevelDestination Whether the destination is a top-level destination.
 */
sealed interface Screen {
  val route: String
  val name: String
  val isTopLevelDestination: Boolean
}

/** Authentification navigation Screen */
sealed class AuthScreen(
    override val route: String,
    override val name: String,
    override val isTopLevelDestination: Boolean = false,
) : Screen {
  data object Auth :
      AuthScreen(
          route = "auth",
          name = "Authentification",
      )
}

/** Intermediate screens that only redirect the user to another destination. */
sealed class TransitionScreen(
    override val route: String,
    override val name: String,
    override val isTopLevelDestination: Boolean = false,
) : Screen {
  data object RoleLoading :
      TransitionScreen(
          route = "role_loading",
          name = "Role loading",
      )
}

/** Screens of the onboarding flow, shown to a signed-in user who has no profile yet. */
sealed class OnboardingScreen(
    override val route: String,
    override val name: String,
    override val isTopLevelDestination: Boolean = false,
) : Screen {

  data object CreateProfile :
      OnboardingScreen(
          route = "create_profile",
          name = "Create a profile",
      )

  data object CaregiverChoice :
      OnboardingScreen(
          route = "caregiver_choice",
          name = "Create CareCircle or Enter Invitation",
      )

  data object CreateCareCircle :
      OnboardingScreen(
          route = "create_carecircle",
          name = "Create a CareCircle",
      )

  data object EnterInvitation :
      OnboardingScreen(
          route = "enter_invitation",
          name = "Enter an invitation",
      )
}

/**
 * Caregiver's navigation Screen.
 *
 * See Figma for more information on each Screen.
 */
sealed class CaregiverScreen(
    override val route: String,
    override val name: String,
    override val isTopLevelDestination: Boolean = false,
) : Screen {
  data object Home :
      CaregiverScreen(
          route = "caregiver_home",
          name = "Home",
          isTopLevelDestination = true,
      )

  data object Upload :
      CaregiverScreen(
          route = "caregiver_upload",
          name = "Upload Texts, Pictures or Audios",
          isTopLevelDestination = true,
      )

  data object UploadText :
      CaregiverScreen(
          route = "caregiver_upload_text",
          name = "Upload Text",
      )

  data object UploadPicture :
      CaregiverScreen(
          route = "caregiver_upload_picture",
          name = "Upload Picture",
      )

  data object UploadAudio :
      CaregiverScreen(
          route = "caregiver_upload_audio",
          name = "Upload Audio",
      )

  data object Parameters :
      CaregiverScreen(
          route = "parameters",
          name = "Parameters",
          isTopLevelDestination = true,
      )

  data object SignOut :
      CaregiverScreen(
          route = "caregiver_sign_out",
          name = "Sign out",
      )

  data object CreateInvitationCode :
      CaregiverScreen(
          route = "create_invitation_code",
          name = "Create an invitation code",
      )
}

/**
 * Patient's navigation destinations.
 *
 * See Figma for more information on the design.
 */
sealed class PatientScreen(
    override val route: String,
    override val name: String,
    override val isTopLevelDestination: Boolean = false,
) : Screen {
  data object Home :
      PatientScreen(
          route = "patient_home",
          name = "Home",
          isTopLevelDestination = true,
      )

  data object SeeMedias :
      PatientScreen(
          route = "patient_see_medias",
          name = "See Medias",
          isTopLevelDestination = true,
      )

  data object SeePictures :
      PatientScreen(
          route = "patient_see_pictures",
          name = "See Pictures",
      )

  data object Parameters :
      PatientScreen(
          route = "patient_parameters",
          name = "Parameters",
          isTopLevelDestination = true,
      )

  data object SignOut :
      PatientScreen(
          route = "patient_sign_out",
          name = "Sign out",
      )
}

/** Routes of the nested navigation graphs, one per user role. */
object NavGraphs {
  const val CAREGIVER = "caregiver_graph"
  const val PATIENT = "patient_graph"
  const val AUTH = "auth_graph"
  const val ONBOARDING = "onboarding_graph"
}
