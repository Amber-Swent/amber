// Written by Neha Chakraborty and assisted by Github Copilot.
package com.github.se.amber.authorization

import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile

/**
 * Client-side access checks for displaying or enabling role-restricted UI.
 *
 * Firestore Security Rules remain the enforcement boundary for all data access and mutations.
 */
object RoleAccess {
  /** Identifies the role that may perform caregiver-only actions. */
  fun isCaregiver(role: Role): Boolean = role == Role.CAREGIVER

  /** Controls whether the UI offers creation of a new care circle. */
  fun canCreateCareCircle(role: Role): Boolean = role == Role.CAREGIVER

  /** Checks whether the user is a member before exposing any circle-specific UI. */
  fun canViewCareCircle(profile: UserProfile, careCircle: CareCircle): Boolean =
      profile.uid in careCircle.memberIds

  /** Members can edit shared names and people, as well as their own nickname entry. */
  fun canEditCareCircle(profile: UserProfile, careCircle: CareCircle): Boolean =
      canViewCareCircle(profile, careCircle)

  /** Only caregiver members can maintain the shared places list. */
  fun canManagePlaces(profile: UserProfile, careCircle: CareCircle): Boolean =
      isCaregiver(profile.role) && canViewCareCircle(profile, careCircle)

  /** A patient member can start the invitation flow that links their phone once. */
  fun canLinkPatient(profile: UserProfile, careCircle: CareCircle): Boolean =
      profile.role == Role.PATIENT &&
          canViewCareCircle(profile, careCircle) &&
          careCircle.patientId.isEmpty()

  /** Invitations change a circle's membership, so they use the same caregiver-member gate. */
  fun canCreateInvitation(profile: UserProfile, careCircle: CareCircle): Boolean =
      canManagePlaces(profile, careCircle)

  /** Appointments are maintained only by caregivers who belong to the relevant circle. */
  fun canManageAppointment(profile: UserProfile, careCircle: CareCircle): Boolean =
      canManagePlaces(profile, careCircle)

  /** Every member may add a memory to their circle. */
  fun canCreateMedia(profile: UserProfile, careCircle: CareCircle): Boolean =
      canViewCareCircle(profile, careCircle)

  /** Only the member who uploaded a memory may change or remove it. */
  fun canManageMedia(
      profile: UserProfile,
      careCircle: CareCircle,
      mediaItem: MediaItem,
  ): Boolean = canViewCareCircle(profile, careCircle) && mediaItem.authorId == profile.uid

  /** A member may view media shared with the whole circle or explicitly with their user ID. */
  fun canViewMedia(profile: UserProfile, careCircle: CareCircle, mediaItem: MediaItem): Boolean =
      canViewCareCircle(profile, careCircle) &&
          (mediaItem.authorizedIds.isEmpty() || profile.uid in mediaItem.authorizedIds)

  /** Stories are shared, so every member may create, edit, or remove them. */
  fun canManageStory(profile: UserProfile, careCircle: CareCircle): Boolean =
      canViewCareCircle(profile, careCircle)
}
