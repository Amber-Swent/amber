package com.github.se.amber.ui.navigation

//noinspection UsingMaterialAndMaterial3Libraries

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.se.amber.ui.theme.lightGray
import com.github.se.amber.ui.theme.orange

object TopNavigationMenuTestTags {
  const val ROOT = "topNavigationMenuRoot"
  const val TITLE = "topNavigationMenuTitle"
  const val BACK_BUTTON = "topNavigationMenuBackButton"
  const val DIVIDER = "topNavigationMenuDivider"
}

/** Text actions that can be displayed in the top navigation menu. */
// IDEA : associate TopNavigationAction with a function (text to display, associated function),
//  if clicking on CANCEL or CHECKOUT always triggers the same action
enum class TopNavigationAction(val label: String) {
  CANCEL("Cancel"),
  CHECKOUT("Checkout"),
}

/**
 * Displays a top app bar with a back button and an action label and the associate text.
 *
 * @param modifier Modifier applied to the outer column containing the app bar and divider.
 * @param action The action label displayed in the app top bar.
 * @param onBackClick Callback invoked when the user taps the back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopNavigationMenu(
    modifier: Modifier = Modifier,
    action: TopNavigationAction,
    onBackClick: () -> Unit,
) {
  Column(modifier = modifier.fillMaxWidth().testTag(TopNavigationMenuTestTags.ROOT)) {
    TopAppBar(
        title = {
          Text(
              action.label,
              modifier = Modifier.testTag(TopNavigationMenuTestTags.TITLE),
              color = orange,
          )
        },
        navigationIcon = {
          IconButton(
              onClick = { onBackClick() },
              modifier = Modifier.testTag(TopNavigationMenuTestTags.BACK_BUTTON),
          ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                tint = orange,
                contentDescription = "Back",
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
    )
    HorizontalDivider(
        Modifier.testTag(TopNavigationMenuTestTags.DIVIDER),
        thickness = 1.dp,
        color = lightGray,
    )
  }
}
