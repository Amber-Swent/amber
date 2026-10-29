// Written by Lou-Anne Maier.
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by Alaabrine, with assistance from Cursor.
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.DIVIDER_THICKNESS

internal const val CANCEL_LABEL = "Cancel"
internal const val CHECKOUT_LABEL = "Checkout"
internal const val BACK_CONTENT_DESCRIPTION = "Back"

/** Stable tags for the top bar, its action label, the back control, and the divider. */
object TopNavigationMenuTestTags {
  const val TOP_NAVIGATION_MENU = "TopNavigationMenu_test_tag_menu"
  const val TITLE = "TopNavigationMenu_test_tag_title"
  const val BACK_BUTTON = "TopNavigationMenu_test_tag_back_button"
  const val DIVIDER = "TopNavigationMenu_test_tag_divider"
}

/**
 * Label shown in [TopNavigationMenu]. The click itself is [TopNavigationMenu]'s `onBackClick`; this
 * enum only chooses the words.
 *
 * @property label Text shown in the bar.
 */
enum class TopNavigationAction(val label: String) {
  CANCEL(CANCEL_LABEL),
  CHECKOUT(CHECKOUT_LABEL),
}

/**
 * Top bar with a back arrow, an action label, and a divider.
 *
 * The bar background is `background`. The title and back icon use `secondary`. Callers on a
 * top-level destination should not show this bar; the back action there is easy to confuse with
 * leaving the app.
 *
 * @param modifier Modifier applied to the column that holds the bar and the divider.
 * @param action Label displayed in the bar.
 * @param onBackClick Called when the back arrow is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopNavigationMenu(
    modifier: Modifier = Modifier,
    action: TopNavigationAction = TopNavigationAction.CANCEL,
    onBackClick: () -> Unit = {},
) {
  Column(
      modifier = modifier.fillMaxWidth().testTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU)
  ) {
    TopAppBar(
        title = {
          Text(
              text = action.label,
              modifier = Modifier.testTag(TopNavigationMenuTestTags.TITLE),
              style = MaterialTheme.typography.titleLarge,
          )
        },
        navigationIcon = {
          IconButton(
              onClick = onBackClick,
              modifier = Modifier.testTag(TopNavigationMenuTestTags.BACK_BUTTON),
          ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = BACK_CONTENT_DESCRIPTION,
            )
          }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.secondary,
                navigationIconContentColor = MaterialTheme.colorScheme.secondary,
            ),
    )
    HorizontalDivider(
        modifier = Modifier.testTag(TopNavigationMenuTestTags.DIVIDER),
        thickness = DIVIDER_THICKNESS,
        color = MaterialTheme.colorScheme.outline,
    )
  }
}

/** Preview of [TopNavigationMenu] using the Amber application theme. */
@Preview(showBackground = true)
@Composable
internal fun TopNavigationMenuPreview() {
  // Omit [action] so the default CANCEL branch is exercised by preview/tests.
  AmberAppTheme(dynamicColor = false) { TopNavigationMenu() }
}
