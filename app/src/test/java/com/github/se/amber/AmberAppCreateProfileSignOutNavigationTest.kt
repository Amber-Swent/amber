// Written by Yuhan YANG, with assistance from Cursor.
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by Alaabrine, with assistance from Cursor (Grok).

package com.github.se.amber

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.credentials.Credential
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.github.se.amber.data.circle.ActiveCircleRepositoryProvider
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.github.se.amber.model.authentication.AuthUser
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.authentication.signOut.PreviewActiveCircleRepository
import com.github.se.amber.ui.navigation.AuthScreen
import com.github.se.amber.ui.navigation.OnboardingScreen
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.navigation.TransitionScreen
import com.github.se.amber.ui.onboarding.createProfile.CreateProfileTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers leaving Create Profile for the auth graph.
 *
 * Role loading and Create Profile build their ViewModels from the default repository providers.
 * Those providers are replaced here so the navigation runs without Firebase.
 */
@RunWith(RobolectricTestRunner::class)
class AmberAppCreateProfileSignOutNavigationTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Before
  fun stubDefaultProviders() {
    mockkObject(AuthRepositoryProvider)
    every { AuthRepositoryProvider.repository } returns SignedInAuthRepository

    mockkObject(ProfileRepositoryProvider.Companion)
    every { ProfileRepositoryProvider.default } returns
        ProfileRepositoryProvider { NoProfileRepository }

    // SignOutViewModel's default reads the active circle, which opens Firebase.
    mockkObject(ActiveCircleRepositoryProvider.Companion)
    every { ActiveCircleRepositoryProvider.default } returns
        ActiveCircleRepositoryProvider { PreviewActiveCircleRepository }
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun createProfileSignOut_opensAuth() {
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

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      navController.currentDestination?.route == OnboardingScreen.CreateProfile.route
    }
    composeTestRule.onNodeWithTag(CreateProfileTestTags.NAME_INPUT).assertIsDisplayed()

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      navController.currentDestination?.route == AuthScreen.Auth.route
    }
    assertEquals(AuthScreen.Auth.route, navController.currentDestination?.route)
  }
}

private object SignedInAuthRepository : AuthRepository {
  override val currentUser: AuthUser = AuthUser("uid123")

  override suspend fun signInWithGoogle(credential: Credential): Result<AuthUser> =
      Result.failure(UnsupportedOperationException("sign-in is not part of this test"))

  override suspend fun reauthenticateWithGoogle(credential: Credential): Result<Unit> =
      Result.failure(UnsupportedOperationException("reauthentication is not part of this test"))

  override suspend fun signOut(): Result<Unit> = Result.success(Unit)
}

private object NoProfileRepository : ProfileRepository {
  override suspend fun createProfile(person: Person, role: Role): Result<Unit> =
      Result.success(Unit)

  override suspend fun updateOnboardingProfile(person: Person, role: Role): Result<Unit> =
      Result.success(Unit)

  override suspend fun getCurrentProfile(): Result<UserProfile?> = Result.success(null)

  override suspend fun getProfile(uid: String): Result<UserProfile?> = Result.success(null)
}
