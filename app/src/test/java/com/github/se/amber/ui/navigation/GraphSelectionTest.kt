// this code was written with the aid of AI

package com.github.se.amber.ui.navigation

import com.github.se.amber.model.user.Role
import com.google.firebase.auth.FirebaseUser
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the graph selection functions: [graphForAuthState] (signed-out user goes to the
 * auth graph, signed-in user goes to role loading) and [graphForRole] (each [Role] goes to its own
 * navigation graph).
 */
class GraphSelectionTest {
  @Test fun noUser_goesToAuth() = assertEquals(NavGraphs.AUTH, graphForAuthState(null))

  @Test
  fun signedInUser_goesToRoleLoading() =
      assertEquals(NavGraphs.ROLE_LOADING, graphForAuthState(mockk<FirebaseUser>()))

  @Test
  fun patient_goesToPatientGraph() = assertEquals(NavGraphs.PATIENT, graphForRole(Role.PATIENT))

  @Test
  fun caregiver_goesToCaregiverGraph() =
      assertEquals(NavGraphs.CAREGIVER, graphForRole(Role.CAREGIVER))
}
