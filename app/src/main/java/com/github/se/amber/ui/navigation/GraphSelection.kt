// this code was written with the aid of AI
package com.github.se.amber.ui.navigation

import com.github.se.amber.model.user.Role
import com.google.firebase.auth.FirebaseUser

/** Navigation to the sign in screen if not connected, or to the charging screen the time to get the
 * role of the user */
fun graphForAuthState(user: FirebaseUser?): String =
    if (user == null) NavGraphs.AUTH else NavGraphs.ROLE_LOADING

/** Navigation to the Graph once we know the role */
fun graphForRole(role: Role): String =
    when (role) {
        Role.CAREGIVER -> NavGraphs.CAREGIVER
        Role.PATIENT -> NavGraphs.PATIENT
    }