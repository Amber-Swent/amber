// Written by Neha Chakraborty and assisted by Github Copilot.
package com.github.se.amber.authorization

import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleAccessTest {
  private val caregiver = UserProfile(uid = "caregiver-id", role = Role.CAREGIVER)
  private val patient = UserProfile(uid = "patient-id", role = Role.PATIENT)
  private val outsider = UserProfile(uid = "outsider-id", role = Role.CAREGIVER)
  private val careCircle = CareCircle(memberIds = listOf(caregiver.uid, patient.uid))

  @Test
  fun caregiverRoleEnablesCaregiverActions() {
    assertTrue(RoleAccess.isCaregiver(caregiver.role))
    assertTrue(RoleAccess.canCreateCareCircle(caregiver.role))
  }

  @Test
  fun patientRoleCannotPerformCaregiverActions() {
    assertFalse(RoleAccess.isCaregiver(patient.role))
    assertFalse(RoleAccess.canCreateCareCircle(patient.role))
  }

  @Test
  fun memberCanAccessSharedCircleContent() {
    assertTrue(RoleAccess.canViewCareCircle(patient, careCircle))
    assertTrue(RoleAccess.canEditCareCircle(patient, careCircle))
    assertTrue(RoleAccess.canCreateMedia(patient, careCircle))
    assertTrue(RoleAccess.canManageStory(patient, careCircle))
  }

  @Test
  fun nonMemberCannotAccessSharedCircleContent() {
    assertFalse(RoleAccess.canViewCareCircle(outsider, careCircle))
    assertFalse(RoleAccess.canEditCareCircle(outsider, careCircle))
    assertFalse(RoleAccess.canCreateMedia(outsider, careCircle))
    assertFalse(RoleAccess.canManageStory(outsider, careCircle))
  }

  @Test
  fun caregiverMemberCanManageCareCircleResources() {
    assertTrue(RoleAccess.canManagePlaces(caregiver, careCircle))
    assertTrue(RoleAccess.canCreateInvitation(caregiver, careCircle))
    assertTrue(RoleAccess.canManageAppointment(caregiver, careCircle))
  }

  @Test
  fun caregiverNonMemberCannotManageCareCircleResources() {
    assertFalse(RoleAccess.canManagePlaces(outsider, careCircle))
    assertFalse(RoleAccess.canCreateInvitation(outsider, careCircle))
    assertFalse(RoleAccess.canManageAppointment(outsider, careCircle))
  }

  @Test
  fun patientMemberCannotManageCareCircleResources() {
    assertFalse(RoleAccess.canManagePlaces(patient, careCircle))
    assertFalse(RoleAccess.canCreateInvitation(patient, careCircle))
    assertFalse(RoleAccess.canManageAppointment(patient, careCircle))
  }

  @Test
  fun memberCanManageTheirOwnMedia() {
    assertTrue(RoleAccess.canManageMedia(caregiver, careCircle, media(authoredBy = caregiver.uid)))
  }

  @Test
  fun memberCannotManageAnotherMembersMedia() {
    assertFalse(RoleAccess.canManageMedia(patient, careCircle, media(authoredBy = caregiver.uid)))
  }

  @Test
  fun nonMemberCannotManageMediaEvenWhenTheyAreTheAuthor() {
    assertFalse(RoleAccess.canManageMedia(outsider, careCircle, media(authoredBy = outsider.uid)))
  }

  @Test
  fun memberCanViewMediaSharedWithWholeCircle() {
    assertTrue(RoleAccess.canViewMedia(patient, careCircle, media()))
  }

  @Test
  fun memberCanViewMediaExplicitlySharedWithThem() {
    assertTrue(
        RoleAccess.canViewMedia(patient, careCircle, media(authorizedIds = listOf(patient.uid)))
    )
  }

  @Test
  fun memberCannotViewMediaSharedWithAnotherMember() {
    assertFalse(
        RoleAccess.canViewMedia(patient, careCircle, media(authorizedIds = listOf(caregiver.uid)))
    )
  }

  @Test
  fun nonMemberCannotViewPublicMedia() {
    assertFalse(RoleAccess.canViewMedia(outsider, careCircle, media()))
  }

  private fun media(
      authoredBy: String = caregiver.uid,
      authorizedIds: List<String> = emptyList(),
  ): MediaItem = MediaItem.Picture(authorId = authoredBy, authorizedIds = authorizedIds)
}
