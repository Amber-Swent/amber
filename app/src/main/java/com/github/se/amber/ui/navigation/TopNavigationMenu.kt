package com.github.se.amber.ui.navigation

//noinspection UsingMaterialAndMaterial3Libraries

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
//import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.se.amber.ui.theme.lightGray
import com.github.se.amber.ui.theme.orange

/** Text actions that can be displayed in the top navigation menu. */
enum class TopNavigationAction{
    CANCEL,
    CHECKOUT,
}

/**
 * Converts a top navigation action to the text shown to the user.
 *
 * @param text The TopNavigationAction whose display text is required.
 * @return The text to display for the given TopNavigationAction
 */
private fun topNaviActionToString(text : TopNavigationAction): String{
    return when (text) {
        TopNavigationAction.CANCEL -> "Cancel"
        TopNavigationAction.CHECKOUT -> "Checkout"
    }
}

/**
 * Displays a top app bar with a back button and an action label and the associate text.
 *
 * @param modifier Modifier applied to the outer column containing the app bar and divider.
 * @param text The action label displayed in the app top bar.
 * @param action Callback invoked when the user click on the label.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopNavigationMenu(
    modifier: Modifier = Modifier,
    // FIXME : associate TopNavigationAction with a function (text to display, associated function),
    //  if clicking on CANCEL or CHECKOUT always triggers the same action

    text: TopNavigationAction,
    action: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {

        TopAppBar(
            title = {
                Text(topNaviActionToString(text), color = orange)
            },
            navigationIcon = {
                IconButton(
                    onClick = { action() },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        tint = orange,
                        contentDescription = "Back",
                    )
                }
            },
        )

        HorizontalDivider(Modifier, thickness = 1.dp, color = lightGray)
    }
}
