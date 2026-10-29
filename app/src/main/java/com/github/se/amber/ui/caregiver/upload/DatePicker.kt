/* Written by LaMaier, with assistance from
 * Claude (Anthropic) via Claude Code.
 * Modified with assistance from OpenAI Codex.
 */
package com.github.se.amber.ui.caregiver.upload

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.ui.caregiver.upload.DatePickerTestTags.CANCEL_BUTTON
import com.github.se.amber.ui.caregiver.upload.DatePickerTestTags.DIALOG
import com.github.se.amber.ui.caregiver.upload.DatePickerTestTags.DIALOG_LIST
import com.github.se.amber.ui.caregiver.upload.DatePickerTestTags.DONE_BUTTON
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.BORDER_THICKNESS
import com.github.se.amber.ui.theme.DATE_PICKER_DIALOG_WIDTH_FRACTION
import com.github.se.amber.ui.theme.DATE_PICKER_LIST_HEIGHT_FRACTION
import com.github.se.amber.ui.theme.DIVIDER_THICKNESS
import com.github.se.amber.ui.theme.MAX_VISIBLE_ITEM
import com.github.se.amber.ui.theme.PADDING_MEDIUM
import com.github.se.amber.ui.theme.PADDING_SMALL
import com.github.se.amber.ui.theme.ROUND_CORNER
import com.github.se.amber.ui.theme.WEIGHT_ONE
import java.time.LocalDate
import java.time.Month
import java.time.Year
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

internal object DatePickerTestTags {
  const val DIVIDER = "date_picker_divider"

  const val DIALOG = "date_picker_dialog"

  fun dialogTitle(field: DateField) = "date_picker_dialog_title_${field.name}"

  const val DIALOG_LIST = "date_picker_dialog_list"
  const val DONE_BUTTON = "date_picker_done_button"
  const val CANCEL_BUTTON = "date_picker_ancel_button"

  fun box(field: DateField) = "date_picker_box_${field.name}"

  fun boxValue(field: DateField) = "date_picker_box_value_${field.name}"

  fun item(index: Int) = "date_picker_item_$index"
}

internal const val MIN_MEMORY_YEAR = 1900

enum class DateField(val label: String) {
  YEAR("Year"),
  MONTH("Month"),
  DAY("Day");

  /**
   * Returns the valid values for this date field.
   *
   * @param date The date whose year and month determine the valid days.
   * @param currentYear The latest year available for a memory.
   */
  fun range(date: MemoryDate, currentYear: Int = Year.now().value): IntRange =
      when (this) {
        YEAR -> MIN_MEMORY_YEAR..currentYear
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
   * @param locale The locale used for month names.
   * @return The label shown for this value in the selection dialog.
   */
  fun format(value: Int, locale: Locale = Locale.getDefault()): String =
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
   * @param locale The locale used for month names.
   * @return The label shown in the date box.
   */
  fun formatCompact(value: Int, locale: Locale = Locale.getDefault()): String =
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

  // Displays the date field (c.f year, month, and day)
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .border(
                  width = BORDER_THICKNESS,
                  color = MaterialTheme.colorScheme.tertiary,
                  shape = RoundedCornerShape(size = ROUND_CORNER),
              )
              .clip(shape = RoundedCornerShape(size = ROUND_CORNER))
              .padding(PADDING_SMALL),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    DateField.entries.forEach { field ->
      DateBox(
          field = field,
          value = field.valueIn(date)?.let { field.formatCompact(it) } ?: "Unknown",
          enabled = field != DateField.DAY || date.month != null,
          onClick = { openField = field },
          modifier = Modifier.weight(WEIGHT_ONE).testTag(DatePickerTestTags.box(field)),
      )
    }
  }

  // Displayed a window when a date field is clicked.
  // This window allows you to select a value for the field in question
  openField?.let { field ->
    val range = field.range(date)
    val allowsUnknown = field != DateField.YEAR // Month and day can be unknown
    ScrollablePickerDialog(
        field = field,
        items =
            if (allowsUnknown) listOf("Unknown") + range.map { field.format(it) }
            else range.map { field.format(it) },
        initialIndex =
            field.valueIn(date)?.let { it - range.first + if (allowsUnknown) 1 else 0 } ?: 0,
        onDismiss = { openField = null },
        onSelected = { index ->
          val value =
              if (allowsUnknown && index == 0) null
              else range.first + index - if (allowsUnknown) 1 else 0
          date = field.applyTo(date, value)
          onDateSelected(date)
          openField = null
        },
    )
  }
}

/**
 * Displays one date field in the picker.
 *
 * @param field The year, month, or day represented by this box.
 * @param value The formatted value shown in the box.
 * @param enabled Whether the box can open its selection dialog.
 * @param onClick Opens the selection dialog for this field.
 * @param modifier The layout and styling applied to the box.
 */
@Composable
private fun DateBox(
    field: DateField,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
      verticalArrangement = Arrangement.spacedBy(PADDING_SMALL),
  ) {
    Text(
        text = field.label,
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

/**
 * Displays a dialog for selecting one value from a scrollable list.
 *
 * @param field The date field being edited; its label determines the dialog title.
 * @param items The choices shown in the scrollable list.
 * @param initialIndex The index of the initially selected choice.
 * @param onDismiss Closes the dialog without applying a choice.
 * @param onSelected Receives the index of the confirmed choice.
 */
@Composable
private fun ScrollablePickerDialog(
    field: DateField,
    items: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit,
) {
  Dialog(
      onDismissRequest = onDismiss,
      // lets the dialog use the width we give it.
      // The size depends on the Screen of the user's Android
      properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    // Snapping guarantees the list always rests with one item exactly in the
    // highlighted slot, so the first visible item index IS the selected one.
    val snapFling = rememberSnapFlingBehavior(listState)
    val selectedIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    // Better than [LocalConfiguration.current.screenHeightDp] because
    // Responds to real-time window size changes
    // But require conversion from Float to Dp
    val screenHeight =
        with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val listHeight = screenHeight * DATE_PICKER_LIST_HEIGHT_FRACTION
    val itemHeight = listHeight / MAX_VISIBLE_ITEM

    Column(
        modifier =
            Modifier.fillMaxWidth(fraction = DATE_PICKER_DIALOG_WIDTH_FRACTION)
                .testTag(DIALOG)
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(size = ROUND_CORNER),
                )
                .padding(PADDING_MEDIUM),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PADDING_MEDIUM),
    ) {
      // display "Select <dateFieldName>"
      Text(
          modifier = Modifier.testTag(DatePickerTestTags.dialogTitle(field)),
          text = "Select ${field.label}",
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
      )

      Box(
          modifier =
              Modifier.height(listHeight)
                  .border(
                      width = BORDER_THICKNESS,
                      color = MaterialTheme.colorScheme.tertiary,
                      shape = RoundedCornerShape(ROUND_CORNER),
                  )
      ) {
        LazyColumn(
            state = listState,
            flingBehavior = snapFling,
            // Lets the first and last items reach the center slot
            contentPadding = PaddingValues(vertical = (listHeight - itemHeight) / 2),
            modifier = Modifier.fillMaxWidth().testTag(DIALOG_LIST),
        ) {
          itemsIndexed(items) { index, item ->
            val isSelected = (index == selectedIndex)
            Text(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(itemHeight)
                        .clickable { onSelected(index) }
                        .wrapContentHeight(Alignment.CenterVertically)
                        .testTag(DatePickerTestTags.item(index)),
                text = item,
                style =
                    if (isSelected) MaterialTheme.typography.bodyLarge
                    else MaterialTheme.typography.bodyMedium,
                color =
                    if (isSelected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
          }
        }
      }

      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(PADDING_SMALL),
      ) {
        // Done Button
        Button(
            modifier = Modifier.weight(WEIGHT_ONE).testTag(DONE_BUTTON),
            onClick = { onSelected(selectedIndex) },
            shape = RoundedCornerShape(ROUND_CORNER),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
        ) {
          Text(
              text = "Done",
              style = MaterialTheme.typography.labelLarge,
          )
        }
        // Cancel Button
        Button(
            modifier = Modifier.weight(WEIGHT_ONE).testTag(CANCEL_BUTTON),
            onClick = onDismiss,
            shape = RoundedCornerShape(ROUND_CORNER),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
        ) {
          Text(
              text = "Cancel",
              style = MaterialTheme.typography.labelLarge,
          )
        }
      }
    }
  }
}

/** Preview of [DisplayScrollableDatePicker] using the Amber application theme. */
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun DisplayScrollableDatePickerPreview() {
  AmberAppTheme(dynamicColor = false) { DisplayScrollableDatePicker() }
}
