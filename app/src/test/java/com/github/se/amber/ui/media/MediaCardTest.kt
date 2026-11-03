package com.github.se.amber.ui.media

import androidx.compose.ui.test.junit4.createComposeRule
import com.github.se.amber.ui.theme.AmberAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaCardTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun mediaCard_composesWithoutCrashing() {
    composeTestRule.setContent { AmberAppTheme { MediaCard() } }
    composeTestRule.waitForIdle()
  }
}
