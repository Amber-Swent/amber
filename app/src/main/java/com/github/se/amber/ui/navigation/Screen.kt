package com.github.se.amber.ui.navigation

/**
 * Base interface for all navigation destinations (Screen).
 * Ensures consistent structure across all user roles
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

  data object SeePictures :
      PatientScreen(
          route = "patient_pictures",
          name = "Pictures",
          isTopLevelDestination = true,
      )
}
