package com.github.se.amber

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.amber.resources.C
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest : TestCase() {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun test() = run {
    step("Start Main Activity") {
      composeTestRule.onNodeWithTag(C.Tag.main_screen_container).assertIsDisplayed()
    }
  }
}
