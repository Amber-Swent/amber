package com.github.se.amber.ui.caregiver.upload

import com.github.se.amber.ui.theme.AmberAppTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import java.time.LocalDate
import java.time.Month
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Fake for the `onDateSelected` callback: records every selection, never touches the network. */
private class FakeDateSelectionListener {
    val selections = mutableListOf<LocalDate>()

    fun onDateSelected(year: Int, month: Int, day: Int) {
        selections += LocalDate.of(year, month, day)
    }
}

/**
 * UI tests for [DisplayScrollableDatePicker]. Everything is located through test tags, and expected
 * values are derived from the date under test (never from displayed strings).
 */
class  CaregiverUtilsAndroidTest{

//------------------DisplayScrollableDatePickerTest-------------------------

    @get:Rule val composeRule = createComposeRule()

    private val listener = FakeDateSelectionListener()

    // Jan 31 of a leap year: lets us test month clamping (31 -> 29) and a 31-day month.
    private val initialDate = LocalDate.of(2024, Month.JANUARY, 31)

    // ---------- helpers ----------

    private fun launch(date: LocalDate = initialDate) {
        composeRule.setContent {
            AmberAppTheme(dynamicColor = false) {
                DisplayScrollableDatePicker(initialDate = date, onDateSelected = listener::onDateSelected)
            }
        }
    }

    private fun openDialog(field: DateField) {
        composeRule.onNodeWithTag(DatePickerTestTags.box(field)).performClick()
    }

    private fun selectItem(index: Int) {
        composeRule.onNodeWithTag(DatePickerTestTags.DIALOG_LIST).performScrollToIndex(index)
        composeRule.onNodeWithTag(DatePickerTestTags.item(index)).performClick()
    }

    private fun tapDone() {
        composeRule.onNodeWithTag(DatePickerTestTags.DONE_BUTTON).performClick()
    }

    private fun tapCancel() {
        composeRule.onNodeWithTag(DatePickerTestTags.CANCEL_BUTTON).performClick()
    }

    // ---------- initial display ----------
    @Test
    fun yearBox_isDisplayed() {
        launch()
        composeRule.onNodeWithTag(DatePickerTestTags.box(DateField.YEAR)).assertIsDisplayed()
    }

    @Test
    fun monthBox_isDisplayed() {
        launch()
        composeRule.onNodeWithTag(DatePickerTestTags.box(DateField.MONTH)).assertIsDisplayed()
    }

    @Test
    fun dayBox_isDisplayed() {
        launch()
        composeRule.onNodeWithTag(DatePickerTestTags.box(DateField.DAY)).assertIsDisplayed()
    }

    @Test
    fun yearBox_showsYearOfInitialDate() {
        launch()
        composeRule
            .onNodeWithTag(DatePickerTestTags.boxValue(DateField.YEAR), useUnmergedTree = true)
            .assertTextEquals(DateField.YEAR.format(DateField.YEAR.valueIn(initialDate)))
    }

    @Test
    fun monthBox_showsMonthOfInitialDate() {
        launch()
        composeRule
            .onNodeWithTag(DatePickerTestTags.boxValue(DateField.MONTH), useUnmergedTree = true)
            .assertTextEquals(DateField.MONTH.format(DateField.MONTH.valueIn(initialDate)))
    }

    @Test
    fun dayBox_showsDayOfInitialDate() {
        launch()
        composeRule
            .onNodeWithTag(DatePickerTestTags.boxValue(DateField.DAY), useUnmergedTree = true)
            .assertTextEquals(DateField.DAY.format(DateField.DAY.valueIn(initialDate)))
    }

    @Test
    fun dialog_isNotDisplayedInitially() {
        launch()
        composeRule.onNodeWithTag(DatePickerTestTags.DIALOG).assertDoesNotExist()
    }

    @Test
    fun callback_isNotCalledInitially() {
        launch()
        assertEquals(emptyList<LocalDate>(), listener.selections)
    }

    // ---------- opening the dialog ----------

    @Test
    fun clickingYearBox_opensYearDialog() {
        launch()
        openDialog(DateField.YEAR)
        composeRule.onNodeWithTag(DatePickerTestTags.dialogTitle(DateField.YEAR)).assertIsDisplayed()
    }

    @Test
    fun clickingMonthBox_opensMonthDialog() {
        launch()
        openDialog(DateField.MONTH)
        composeRule.onNodeWithTag(DatePickerTestTags.dialogTitle(DateField.MONTH)).assertIsDisplayed()
    }

    @Test
    fun clickingDayBox_opensDayDialog() {
        launch()
        openDialog(DateField.DAY)
        composeRule.onNodeWithTag(DatePickerTestTags.dialogTitle(DateField.DAY)).assertIsDisplayed()
    }

    @Test
    fun dialog_displaysList() {
        launch()
        openDialog(DateField.DAY)
        composeRule.onNodeWithTag(DatePickerTestTags.DIALOG_LIST).assertIsDisplayed()
    }

    @Test
    fun dialog_displaysDoneButton() {
        launch()
        openDialog(DateField.DAY)
        composeRule.onNodeWithTag(DatePickerTestTags.DONE_BUTTON).assertIsDisplayed()
    }

    @Test
    fun dialog_displaysCancelButton() {
        launch()
        openDialog(DateField.DAY)
        composeRule.onNodeWithTag(DatePickerTestTags.CANCEL_BUTTON).assertIsDisplayed()
    }

    @Test
    fun dialog_showsCurrentValueOfFieldAsInitiallySelectedItem() {
        launch()
        openDialog(DateField.DAY)
        val index = DateField.DAY.valueIn(initialDate) - DateField.DAY.range(initialDate).first
        composeRule.onNodeWithTag(DatePickerTestTags.item(index)).assertIsDisplayed()
    }

    // ---------- list content ----------
    @Test
    fun dayList_hasNoItemBeyondLengthOfMonth() {
        val february = LocalDate.of(2023, Month.FEBRUARY, 10)
        launch(february)
        openDialog(DateField.DAY)
        val lastIndex = DateField.DAY.range(february).count() - 1
        composeRule.onNodeWithTag(DatePickerTestTags.DIALOG_LIST).performScrollToIndex(lastIndex)
        composeRule.onNodeWithTag(DatePickerTestTags.item(lastIndex + 1)).assertDoesNotExist()
    }

    // ---------- dismissing ----------

    @Test
    fun cancel_closesDialog() {
        launch()
        openDialog(DateField.DAY)
        tapCancel()
        composeRule.onNodeWithTag(DatePickerTestTags.DIALOG).assertDoesNotExist()
    }

    @Test
    fun cancel_doesNotCallCallback() {
        launch()
        openDialog(DateField.DAY)
        tapCancel()
        assertEquals(emptyList<LocalDate>(), listener.selections)
    }
    // ---------- confirming with Done ----------

    @Test
    fun done_closesDialog() {
        launch()
        openDialog(DateField.DAY)
        tapDone()
        composeRule.onNodeWithTag(DatePickerTestTags.DIALOG).assertDoesNotExist()
    }

    @Test
    fun done_withoutScrolling_callsCallbackWithUnchangedDate() {
        launch()
        openDialog(DateField.DAY)
        tapDone()
        assertEquals(listOf(initialDate), listener.selections)
    }

    @Test
    fun done_afterScrolling_callsCallbackExactlyOnce() {
        launch()
        openDialog(DateField.DAY)
        composeRule.onNodeWithTag(DatePickerTestTags.DIALOG_LIST).performScrollToIndex(0)
        composeRule.waitForIdle()
        tapDone()
        assertEquals(1, listener.selections.size)
    }

    // ---------- selecting an item by clicking it ----------

    @Test
    fun clickingDayItem_closesDialog() {
        launch()
        openDialog(DateField.DAY)
        selectItem(0)
        composeRule.onNodeWithTag(DatePickerTestTags.DIALOG).assertDoesNotExist()
    }

    @Test
    fun clickingDayItem_reportsSelectedDay() {
        launch()
        openDialog(DateField.DAY)
        selectItem(0)
        assertEquals(listOf(initialDate.withDayOfMonth(DateField.DAY.range(initialDate).first)), listener.selections)
    }

    @Test
    fun clickingMonthItem_reportsSelectedMonth() {
        launch()
        openDialog(DateField.MONTH)
        selectItem(Month.MARCH.value - DateField.MONTH.range(initialDate).first)
        assertEquals(listOf(initialDate.withMonth(Month.MARCH.value)), listener.selections)
    }

    @Test
    fun clickingYearItem_reportsSelectedYear() {
        launch()
        openDialog(DateField.YEAR)
        selectItem(0)
        assertEquals(listOf(initialDate.withYear(DateField.YEAR.range(initialDate).first)), listener.selections)
    }

    @Test
    fun clickingShorterMonth_clampsDayToLastDayOfThatMonth() {
        launch()
        openDialog(DateField.MONTH)
        selectItem(Month.FEBRUARY.value - DateField.MONTH.range(initialDate).first)
        assertEquals(listOf(initialDate.withMonth(Month.FEBRUARY.value)), listener.selections)
    }

    @Test
    fun cancel_afterScrolling_leavesBoxUnchanged() {
        launch()
        openDialog(DateField.DAY)
        composeRule.onNodeWithTag(DatePickerTestTags.DIALOG_LIST).performScrollToIndex(0)
        tapCancel()
        composeRule
            .onNodeWithTag(DatePickerTestTags.boxValue(DateField.DAY), useUnmergedTree = true)
            .assertTextEquals(DateField.DAY.format(DateField.DAY.valueIn(initialDate)))
    }
}