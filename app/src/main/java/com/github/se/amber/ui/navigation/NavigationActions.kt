package com.github.se.amber.ui.navigation

import androidx.navigation.NavHostController

/**
 * Wrapper around [NavHostController] that centralizes the navigation logic of the app.
 *
 * It lets the UI navigate between [Screen]s without handling the back stack directly: top-level
 * destinations replace any existing copy of themselves in the back stack, while other destinations
 * are simply pushed on top. The class is `open` (as are its methods) so it can be mocked or
 * overridden in tests.
 *
 * @property navController The controller used to perform the actual navigation.
 */
open class NavigationActions(private val navController: NavHostController) {
  /**
   * Navigate to the specified screen according to the screen level.
   *
   * @param screen The screen to navigate to
   */
  open fun navigateTo(screen: Screen) {
    // if the user is already on the top-level destination nothing changes
    if (screen.isTopLevelDestination && currentRoute() == screen.route) {
      return
    }

    navController.navigate(screen.route) {
      // if the screen is a top-level destination it removes an existing copy from the back
      // stack and recreate it
      if (screen.isTopLevelDestination) {
        launchSingleTop = true
        popUpTo(screen.route) { inclusive = true }
      }
      // if the screen is not a top-level destination it only adds a new copy of if

      // restore the previous stored state of the screen, except for the authentification
      if (screen !is AuthScreen.Auth) {
        restoreState = true
      }
    }
  }

  /** Navigate back to the previous screen. */
  open fun goBack() {
    navController.popBackStack()
  }

  /**
   * Get the current route of the navigation controller.
   *
   * @return The current route
   */
  open fun currentRoute(): String {
    return navController.currentDestination?.route ?: ""
  }
}
