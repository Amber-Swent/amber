// Written by Alaabrine, with assistance from Cursor
// Modified by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.credentials.Credential
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.github.se.amber.data.invitation.InvitationRedemptionRepository
import com.github.se.amber.data.invitation.InvitationRedemptionRepositoryProvider
import com.github.se.amber.data.invitation.InvitationRedemptionResult
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.data.session.SessionRepository
import com.github.se.amber.data.session.SessionRepositoryProvider
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.github.se.amber.model.authentication.AuthUser
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.navigation.AuthScreen
import com.github.se.amber.ui.navigation.OnboardingScreen
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.navigation.TransitionScreen
import com.github.se.amber.ui.onboarding.createProfile.CreateProfileTestTags
import com.github.se.amber.ui.patient.PatientUtilsTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers [AmberApp]'s route from role loading to the missing-circle onboarding step.
 *
 * The screens build their ViewModels from the default repository providers. Those providers are
 * replaced here so the navigation runs without Firebase.
 */
@RunWith(RobolectricTestRunner::class)
class AmberAppNeedsCareCircleNavigationTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Before
  fun stubDefaultProviders() {
    mockkObject(AuthRepositoryProvider)
    every { AuthRepositoryProvider.repository } returns UnusedAuthRepository

    mockkObject(SessionRepositoryProvider.Companion)
    every { SessionRepositoryProvider.default } returns
        SessionRepositoryProvider {
          SessionRepository { AuthRepositoryProvider.repository.signOut() }
        }

    mockkObject(ProfileRepositoryProvider.Companion)
    mockkObject(InvitationRedemptionRepositoryProvider.Companion)
    every { InvitationRedemptionRepositoryProvider.default } returns
        InvitationRedemptionRepositoryProvider { UnusedInvitationRepository }
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun caregiverWithoutCircle_opensCreateProfile() {
    val navController = launch(profile(Role.CAREGIVER))

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      navController.currentDestination?.route == OnboardingScreen.CreateProfile.route
    }

    assertEquals(OnboardingScreen.CreateProfile.route, navController.currentDestination?.route)
    composeTestRule.onNodeWithTag(CreateProfileTestTags.NAME_INPUT).assertIsDisplayed()
  }

  @Test
  fun patientWithoutCircle_opensCreateProfile() {
    val navController = launch(profile(Role.PATIENT))

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      navController.currentDestination?.route == OnboardingScreen.CreateProfile.route
    }

    assertEquals(OnboardingScreen.CreateProfile.route, navController.currentDestination?.route)
    composeTestRule.onNodeWithTag(CreateProfileTestTags.NAME_INPUT).assertIsDisplayed()
  }

  @Test
  fun patientWithoutCircle_signOutFromCreateProfileOpensAuth() {
    val navController = launch(profile(Role.PATIENT))
    openCreateProfile(navController)

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      navController.currentDestination?.route == AuthScreen.Auth.route
    }
    assertEquals(AuthScreen.Auth.route, navController.currentDestination?.route)
  }

  @Test
  fun caregiverWithoutCircle_cancelFromChoiceReturnsToCreateProfile() {
    val navController = launch(profile(Role.CAREGIVER))
    openCreateProfile(navController)
    enterNameAndChoose(CreateProfileTestTags.CAREGIVER_BUTTON)

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      navController.currentDestination?.route == OnboardingScreen.CaregiverChoice.route
    }
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      navController.currentDestination?.route == OnboardingScreen.CreateProfile.route
    }
    assertEquals(OnboardingScreen.CreateProfile.route, navController.currentDestination?.route)
  }

  @Test
  fun patientWithoutCircle_cancelFromInvitationReturnsToCreateProfile() {
    val navController = launch(profile(Role.PATIENT))
    openCreateProfile(navController)
    enterNameAndChoose(PatientUtilsTestTags.BUTTON)

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      navController.currentDestination?.route == OnboardingScreen.EnterInvitation.route
    }
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      navController.currentDestination?.route == OnboardingScreen.CreateProfile.route
    }
    assertEquals(OnboardingScreen.CreateProfile.route, navController.currentDestination?.route)
  }

  @Test
  fun caregiverWithoutCircle_roleLoadingIsNotInTheBackStack() {
    val navController = launch(profile(Role.CAREGIVER))
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      navController.currentDestination?.route == OnboardingScreen.CreateProfile.route
    }

    assertFalse(
        navController.currentBackStack.value.any {
          it.destination.route == TransitionScreen.RoleLoading.route
        }
    )
  }

  private fun openCreateProfile(navController: NavHostController) {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      navController.currentDestination?.route == OnboardingScreen.CreateProfile.route
    }
  }

  private fun enterNameAndChoose(roleButtonTag: String) {
    composeTestRule.onNodeWithTag(CreateProfileTestTags.NAME_INPUT).performTextInput("Ada")
    composeTestRule.onNodeWithTag(CreateProfileTestTags.SURNAME_INPUT).performTextInput("Lovelace")
    composeTestRule.onNodeWithTag(roleButtonTag).performScrollTo().performClick()
  }

  private fun launch(profile: UserProfile): NavHostController {
    every { ProfileRepositoryProvider.default } returns
        ProfileRepositoryProvider { profileRepository(profile) }

    lateinit var navController: NavHostController
    composeTestRule.setContent {
      val controller = rememberNavController()
      navController = controller
      AmberAppTheme {
        AmberApp(
            credentialManager = mockk(relaxed = true),
            navController = controller,
            startDestination = TransitionScreen.RoleLoading.route,
        )
      }
    }
    return navController
  }

  private fun profile(role: Role) =
      UserProfile(
          uid = "uid123",
          role = role,
          person = Person(id = "uid123", firstName = "Ada", lastName = "Lovelace"),
          circleIds = emptyList(),
      )

  private fun profileRepository(profile: UserProfile): ProfileRepository =
      object : ProfileRepository {
        override suspend fun createProfile(person: Person, role: Role) = Result.success(Unit)

        override suspend fun updateOnboardingProfile(person: Person, role: Role) =
            Result.success(Unit)

        override suspend fun getCurrentProfile() = Result.success(profile)

        override suspend fun getProfile(uid: String) = Result.success(null)
      }
}

private object UnusedAuthRepository : AuthRepository {
  override val currentUser: AuthUser = AuthUser("uid123")

  override suspend fun signInWithGoogle(credential: Credential): Result<AuthUser> =
      Result.failure(UnsupportedOperationException("sign-in is not part of this test"))

  override suspend fun reauthenticateWithGoogle(credential: Credential): Result<Unit> =
      Result.failure(UnsupportedOperationException("reauthentication is not part of this test"))

  override suspend fun signOut(): Result<Unit> = Result.success(Unit)
}

private object UnusedInvitationRepository : InvitationRedemptionRepository {
  override suspend fun redeemInvitation(code: String): Result<InvitationRedemptionResult> =
      Result.failure(UnsupportedOperationException("redeem is not part of this test"))
}
