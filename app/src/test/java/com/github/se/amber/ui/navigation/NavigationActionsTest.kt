// this code was written with the aid of AI

package com.github.se.amber.ui.navigation

import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.navOptions
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [NavigationActions], using a mocked [NavHostController]. They check that
 * [NavigationActions.navigateTo] skips redundant top-level navigation and passes the right
 * [NavOptions] depending on the screen type, that [NavigationActions.navigateToGraph] clears the
 * entire back stack (so the user cannot go back to the sign-in screen after logging in), and that
 * [NavigationActions.currentRoute] and [NavigationActions.goBack] delegate to the controller.
 */
class NavigationActionsTest {
  private lateinit var navController: NavHostController
  private lateinit var actions: NavigationActions

  private val topLevelScreens =
      listOf(
          CaregiverScreen.Home,
          CaregiverScreen.Upload,
          PatientScreen.Home,
          PatientScreen.SeePictures,
      )

  @Before
  fun setUp() {
    navController = mockk(relaxed = true)
    actions = NavigationActions(navController)
  }

  /**
   * Makes the mocked [controller] report [route] as the current destination, or no destination at
   * all when [route] is null.
   */
  private fun currentRouteIs(route: String?, controller: NavHostController = navController) {
    every { controller.currentDestination } returns
        route?.let { r -> mockk<NavDestination> { every { this@mockk.route } returns r } }
  }

  /**
   * Calls [NavigationActions.navigateTo] for [screen] on a fresh mocked controller, pretending the
   * user is currently on [fromRoute].
   *
   * Captures the [NavOptionsBuilder] configuration passed to [NavHostController.navigate], converts
   * it to [NavOptions], and returns the resulting options so tests can check them.
   */
  private fun optionsUsedToNavigateTo(
      screen: Screen,
      fromRoute: String = "other_route",
  ): NavOptions {
    val controller = mockk<NavHostController>(relaxed = true)
    currentRouteIs(fromRoute, controller)

    val captured = slot<NavOptionsBuilder.() -> Unit>()

    NavigationActions(controller).navigateTo(screen)

    verify(exactly = 1) {
      controller.navigate(
          screen.route,
          capture(captured),
      )
    }

    return navOptions { captured.captured.invoke(this) }
  }

  // ---- currentRoute ----

  @Test
  fun currentRoute_returnsEmptyString_whenNoDestination() {
    currentRouteIs(null)
    assertEquals("", actions.currentRoute())
  }

  @Test
  fun currentRoute_returnsRoute_whenDestinationExists() {
    currentRouteIs(PatientScreen.Home.route)
    assertEquals("patient_home", actions.currentRoute())
  }

  // ---- navigateTo : early return ----

  @Test
  fun topLevel_alreadyDisplayed_doesNotNavigate() {
    currentRouteIs(PatientScreen.Home.route)

    actions.navigateTo(PatientScreen.Home)

    // check that the function navigate was never called
    verify(exactly = 0) { navController.navigate(any<String>(), any<NavOptions>(), any()) }
  }

  @Test
  fun nonTopLevel_alreadyDisplayed_stillNavigates() {
    // check that there are no early return for a screen that is not a top-level destination
    // check that it calls the navigate function
    optionsUsedToNavigateTo(
        CaregiverScreen.UploadText,
        fromRoute = CaregiverScreen.UploadText.route,
    )
  }

  // ---- navigateTo : options ----

  @Test
  fun topLevel_fromAnotherRoute_usesSingleTopAndPopsItselfInclusive() {
    for (screen in topLevelScreens) {
      val options = optionsUsedToNavigateTo(screen)

      assertTrue("${screen.route}: launchSingleTop", options.shouldLaunchSingleTop())
      assertEquals("${screen.route}: popUpTo", screen.route, options.popUpToRoute)
      assertTrue("${screen.route}: inclusive", options.isPopUpToInclusive())
      assertTrue("${screen.route}: restoreState", options.shouldRestoreState())
    }
  }

  @Test
  fun nonTopLevel_isSimplyPushed() {
    val options = optionsUsedToNavigateTo(CaregiverScreen.UploadText)

    assertFalse(options.shouldLaunchSingleTop())
    assertNull(options.popUpToRoute)
    assertTrue(options.shouldRestoreState())
  }

  @Test
  fun authScreen_doesNotRestoreState() {
    val options = optionsUsedToNavigateTo(AuthScreen.Auth)

    assertFalse(options.shouldRestoreState())
  }

  // ---- goBack ----

  @Test
  fun goBack_popsTheBackStackOnce() {
    actions.goBack()

    verify(exactly = 1) { navController.popBackStack() }
  }

  // ---- navigateToGraph ----

  @Test
  fun navigateToGraph_clearsEntireBackStack() {
    val controller = mockk<NavHostController>(relaxed = true)

    every { controller.graph.id } returns 42

    val captured = slot<NavOptionsBuilder.() -> Unit>()

    NavigationActions(controller).navigateToGraph(NavGraphs.PATIENT)

    verify(exactly = 1) {
      controller.navigate(
          NavGraphs.PATIENT,
          capture(captured),
      )
    }

    val options = navOptions { captured.captured.invoke(this) }

    assertEquals(42, options.popUpToId)
    assertTrue(options.isPopUpToInclusive())
  }
}
