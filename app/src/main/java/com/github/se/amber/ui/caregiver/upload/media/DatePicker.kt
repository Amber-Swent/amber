// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.
// Modified by MeiliV, with assistance from OpenAI Codex.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver.upload.media

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.github.se.amber.R
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.ui.caregiver.upload.media.DatePickerTestTags.CANCEL_BUTTON
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.BORDER_THICKNESS
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.theme.DATE_PICKER_WIDTH_FRACTION
import com.github.se.amber.ui.theme.DIVIDER_THICKNESS
import com.github.se.amber.ui.theme.ROUND_CORNER
import com.github.se.amber.ui.theme.WEIGHT_ONE
import com.github.se.amber.ui.theme.YEAR_RANGE
import java.time.LocalDate
import java.time.Month
import java.time.Year
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

internal object DatePickerTestTags {
  const val DIVIDER = "date_picker_divider"
  const val DIALOG = "date_picker_dialog"
  const val DIALOG_LIST = "date_picker_dialog_list"
  const val DONE_BUTTON = "date_picker_done_button"
  const val CANCEL_BUTTON = "date_picker_cancel_button"
  const val DISMISS_BUTTON = CANCEL_BUTTON
  const val MONTH_REQUIRED_ERROR = "date_picker_month_required_error"

  fun dialogTitle(field: DateField) = "date_picker_dialog_title_${field.name}"

  fun box(field: DateField) = "date_picker_box_${field.name}"

  fun boxValue(field: DateField) = "date_picker_box_value_${field.name}"

  fun item(index: Int) = "date_picker_item_$index"
}

/**
 * A part of a [MemoryDate] the user picks on its own.
 *
 * @property labelRes the name shown on the field's box, as a string resource.
 * @property dialogTitleRes the title of the dialog that picks the field's value. It is a whole
 *   sentence per field, so each language can use its own grammar ("Choisir l'année", "Choisir le
 *   mois").
 */
enum class DateField(
    @param:StringRes val labelRes: Int,
    @param:StringRes val dialogTitleRes: Int,
) {
  YEAR(R.string.datePickerYear, R.string.datePickerSelectYear),
  MONTH(R.string.datePickerMonth, R.string.datePickerSelectMonth),
  DAY(R.string.datePickerDay, R.string.datePickerSelectDay);

  /**
   * Returns the valid values for this date field.
   *
   * @param date The date whose year and month determine the valid days.
   * @param currentYear The latest year available for a memory.
   */
  fun range(date: MemoryDate, currentYear: Int = Year.now().value): IntRange =
      when (this) {
        YEAR -> currentYear - YEAR_RANGE..currentYear
        MONTH -> 1..12
        DAY -> 1..(date.month?.let { YearMonth.of(date.year, it).lengthOfMonth() } ?: 0)
      }

  /**
   * Returns this field's value from the given date.
   *
   * @param date The date containing the field value to read.
   */
  fun valueIn(date: MemoryDate): Int? =
      when (this) {
        YEAR -> date.year
        MONTH -> date.month?.value
        DAY -> date.day
      }

  /**
   * Returns a date with this field replaced by [value].
   *
   * @param date The original date to update.
   * @param value The replacement field value, or null for an unknown month or day.
   * @return the MemoryDate updated.
   */
  fun applyTo(date: MemoryDate, value: Int?): MemoryDate =
      when (this) {
        YEAR -> date.copy(year = requireNotNull(value)).withValidDay()
        MONTH ->
            date
                .copy(month = value?.let(Month::of), day = if (value == null) null else date.day)
                .withValidDay()
        DAY -> date.copy(day = if (date.month == null) null else value)
      }

  /**
   * Formats this field's numeric value for a dialog choice. Months use their full localized names;
   * days use two digits, and years remain unchanged.
   *
   * @param value The numeric year, month (1 to 12), or day to display.
   * @param locale The locale used for month names: the app locale, so they are translated.
   * @return The label shown for this value in the selection dialog.
   */
  fun format(value: Int, locale: Locale): String =
      when (this) {
        YEAR -> "$value"
        MONTH -> Month.of(value).getDisplayName(TextStyle.FULL, locale)
        DAY -> value.toString().padStart(2, '0')
      }

  /**
   * Formats this field's value for its date box. Months use short localized names to fit the box;
   * years and days use the same labels as [format].
   *
   * @param value The numeric year, month (1 to 12), or day to display.
   * @param locale The locale used for month names: the app locale, so they are translated.
   * @return The label shown in the date box.
   */
  fun formatCompact(value: Int, locale: Locale): String =
      if (this == MONTH) Month.of(value).getDisplayName(TextStyle.SHORT, locale)
      else format(value, locale)
}

/** Keeps a known day valid when the selected month or year changes. */
private fun MemoryDate.withValidDay(): MemoryDate =
    if (month == null) copy(day = null)
    else copy(day = day?.coerceAtMost(YearMonth.of(year, month).lengthOfMonth()))

/**
 * Displays a picker for a memory date. Month and day may be unknown.
 *
 * @param modifier The layout and styling applied to the picker.
 * @param initialDate The date shown before the user makes a selection.
 * @param onDateSelected Receives the selected date, including any unknown month or day.
 */
@Composable
fun DisplayScrollableDatePicker(
    modifier: Modifier = Modifier,
    initialDate: MemoryDate = MemoryDate(LocalDate.now().year),
    onDateSelected: (MemoryDate) -> Unit = {},
) {
  var date by remember { mutableStateOf(initialDate) }
  var openField by remember { mutableStateOf<DateField?>(null) }
  // Month names follow the app language
  val locale = LocalConfiguration.current.locales[0]

  // Day cannot be chosen until month is specified
  var showMonthRequiredError by remember { mutableStateOf(false) }

  // Displays the date field (c.f year, month, and day)
  Column(modifier = modifier) {
    Row(
        modifier =
            Modifier.fillMaxWidth(DATE_PICKER_WIDTH_FRACTION)
                .border(
                    width = BORDER_THICKNESS,
                    color = MaterialTheme.colorScheme.tertiary,
                    shape = RoundedCornerShape(size = ROUND_CORNER),
                )
                .clip(shape = RoundedCornerShape(size = ROUND_CORNER))
                .padding(CAREGIVER_PADDING_SMALL),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      DateField.entries.forEach { field ->
        DateBox(
            field = field,
            value =
                field.valueIn(date)?.let { field.formatCompact(it, locale) }
                    ?: stringResource(R.string.datePickerUnknown),
            // Disabling the day button when month is not selected would not detect tentative clicks
            // On the day button, so we use onClick to show an error instead
            onClick = {
              if (field == DateField.DAY && date.month == null) {
                showMonthRequiredError = true
              } else {
                openField = field
              }
            },
            modifier = Modifier.weight(WEIGHT_ONE).testTag(DatePickerTestTags.box(field)),
        )
      }
    }
    // Day cannot be selected before the month is
    if (showMonthRequiredError) {
      Text(
          modifier =
              Modifier.align(Alignment.End)
                  .padding(top = CAREGIVER_PADDING_SMALL)
                  .testTag(DatePickerTestTags.MONTH_REQUIRED_ERROR),
          text = stringResource(R.string.datePickerMonthRequired),
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
      )
    }
  }

  // Displayed a window when a date field is clicked.
  // This window allows you to select a value for the field in question
  openField?.let { field ->
    val range = field.range(date)
    val allowsUnknown = field != DateField.YEAR // Month and day can be unknown
    val unknown = stringResource(R.string.datePickerUnknown)
    ScrollablePickerDialog(
        field = field,
        items =
            if (allowsUnknown) listOf(unknown) + range.map { field.format(it, locale) }
            else range.map { field.format(it, locale) },
        initialIndex =
            field.valueIn(date)?.let { it - range.first + if (allowsUnknown) 1 else 0 } ?: 0,
        onDismiss = { openField = null },
        onSelected = { index ->
          val value =
              if (allowsUnknown && index == 0) null
              else range.first + index - if (allowsUnknown) 1 else 0
          date = field.applyTo(date, value)

          // Clear error state
          if (date.month != null) {
            showMonthRequiredError = false
          }
          onDateSelected(date)
          openField = null
        },
    )
  }
}

/**
 * Displays one date field in the picker.
 *
 * @param modifier The layout and styling applied to the box.
 * @param field The year, month, or day represented by this box.
 * @param value The formatted value shown in the box.
 * @param enabled Whether the box can open its selection dialog.
 * @param onClick Opens the selection dialog for this field.
 */
@Composable
private fun DateBox(
    modifier: Modifier = Modifier,
    field: DateField, // No natural default here
    value: String = "",
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val textColor =
      if (enabled) MaterialTheme.colorScheme.onSurface
      else MaterialTheme.colorScheme.onSurfaceVariant

  Column(
      modifier =
          modifier
              .fillMaxWidth()
              .clip(shape = RoundedCornerShape(size = ROUND_CORNER))
              .then(
                  if (isPressed) Modifier.background(MaterialTheme.colorScheme.tertiary)
                  else Modifier
              )
              .clickable(
                  enabled = enabled,
                  interactionSource = interactionSource,
                  indication = null,
                  onClick = onClick,
              ),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL),
  ) {
    Text(
        text = stringResource(field.labelRes),
        color = textColor,
        style = MaterialTheme.typography.bodyMedium,
    )

    HorizontalDivider(
        modifier = Modifier.testTag(DatePickerTestTags.DIVIDER),
        thickness = DIVIDER_THICKNESS,
        color = MaterialTheme.colorScheme.tertiary,
    )
    Text(
        modifier = Modifier.testTag(DatePickerTestTags.boxValue(field)),
        text = value,
        color = textColor,
        style = MaterialTheme.typography.bodyMedium,
    )
  }
}

/** Preview of [DisplayScrollableDatePicker] using the Amber application theme. */
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun DisplayScrollableDatePickerPreview() {
  AmberAppTheme(dynamicColor = false) { DisplayScrollableDatePicker() }
}
