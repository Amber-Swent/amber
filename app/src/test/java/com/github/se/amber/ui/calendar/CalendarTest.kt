// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.calendar

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.se.amber.ui.calendar.CalendarTestTags.CALENDAR
import com.github.se.amber.ui.calendar.CalendarTestTags.MONTH_TITLE
import com.github.se.amber.ui.calendar.CalendarTestTags.NEXT_MONTH_BUTTON
import com.github.se.amber.ui.calendar.CalendarTestTags.PREVIOUS_MONTH_BUTTON
import com.github.se.amber.ui.theme.AmberAppTheme
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Tests [Calendar]: the box, the month navigation, the weekdays and the days. */
@RunWith(RobolectricTestRunner::class)
class CalendarTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  // November has 30 days, October and December have 31.
  private val november = YearMonth.of(2026, 11)

  private fun launch(onDayClick: (LocalDate) -> Unit = {}) {
    // Created here, not in setContent: a ViewModel must not be constructed in a composable.
    val viewModel = CalendarViewModel(november)
    composeTestRule.setContent {
      AmberAppTheme { Calendar(onDayClick = onDayClick, viewModel = viewModel) }
    }
  }

  // The display checks of the calendar are grouped in one test, to launch the screen only once.
  @Test
  fun displaysTheCalendar() {
    launch()
    composeTestRule.onNodeWithTag(CALENDAR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MONTH_TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(PREVIOUS_MONTH_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NEXT_MONTH_BUTTON).assertIsDisplayed()
    for (index in 0 until 7) {
      composeTestRule.onNodeWithTag(CalendarTestTags.weekday(index)).assertIsDisplayed()
    }
    composeTestRule.onNodeWithTag(CalendarTestTags.day(30)).assertIsDisplayed()
  }

  @Test
  fun nextArrowShowsTheNextMonth() {
    launch()
    // The 31st only exists in December and not in november.
    composeTestRule.onNodeWithTag(CalendarTestTags.day(31)).assertDoesNotExist()
    composeTestRule.onNodeWithTag(NEXT_MONTH_BUTTON).performClick()
    composeTestRule.onNodeWithTag(CalendarTestTags.day(31)).assertIsDisplayed()
  }

  @Test
  fun previousArrowShowsThePreviousMonth() {
    launch()
    // The 31st only exists in October and not in november.
    composeTestRule.onNodeWithTag(CalendarTestTags.day(31)).assertDoesNotExist()
    composeTestRule.onNodeWithTag(PREVIOUS_MONTH_BUTTON).performClick()
    composeTestRule.onNodeWithTag(CalendarTestTags.day(31)).assertIsDisplayed()
  }

  @Test
  fun clickingADayCallsOnDayClickWithItsDate() {
    var clicked: LocalDate? = null
    launch(onDayClick = { clicked = it })
    composeTestRule.onNodeWithTag(CalendarTestTags.day(12)).performClick()
    assertEquals(LocalDate.of(2026, 11, 12), clicked)
  }

  @Test
  fun clickingAfterChangingMonthGivesTheDateOfThatMonth() {
    var clicked: LocalDate? = null
    launch(onDayClick = { clicked = it })
    composeTestRule.onNodeWithTag(NEXT_MONTH_BUTTON).performClick()
    composeTestRule.onNodeWithTag(CalendarTestTags.day(5)).performClick()
    assertEquals(LocalDate.of(2026, 12, 5), clicked)
  }
}
