/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.se.amber.ui.caregiver.upload.DatePickerTestTags.CANCEL_BUTTON
import com.github.se.amber.ui.caregiver.upload.DatePickerTestTags.DIALOG
import com.github.se.amber.ui.caregiver.upload.DatePickerTestTags.DIALOG_LIST
import com.github.se.amber.ui.caregiver.upload.DatePickerTestTags.DONE_BUTTON
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.BIG_ROUND_CORNER
import com.github.se.amber.ui.theme.BORDER_THICKNESS
import com.github.se.amber.ui.theme.DIVIDER_THICKNESS
import com.github.se.amber.ui.theme.PADDING_MEDIUM
import com.github.se.amber.ui.theme.PADDING_SMALL
import com.github.se.amber.ui.theme.darkGray
import com.github.se.amber.ui.theme.lightGray
import com.github.se.amber.ui.theme.lightOrange
import java.time.LocalDate

private const val VISIBLE_ITEMS = 5 // number of rows visible at once in the picker

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

enum class DateField(val label: String) {
  YEAR("Year"),
  MONTH("Month"),
  DAY("Day");

  /**
   * Returns the valid values for this date field.
   *
   * @param date date used to determine the valid range
   */
  fun range(date: LocalDate): IntRange =
      when (this) {
        YEAR -> date.year - 100..date.year + 100
        MONTH -> 1..12
        DAY -> 1..date.lengthOfMonth()
      }

  /**
   * Returns this field's value from the given date.
   *
   * @param date date from which to read the value
   */
  fun valueIn(date: LocalDate): Int =
      when (this) {
        YEAR -> date.year
        MONTH -> date.monthValue
        DAY -> date.dayOfMonth
      }

  /**
   * Returns a date with this field replaced by [value].
   *
   * @param date date to update
   * @param value new value for this field
   */
  fun applyTo(date: LocalDate, value: Int): LocalDate =
      when (this) {
        YEAR -> date.withYear(value)
        MONTH -> date.withMonth(value)
        DAY -> date.withDayOfMonth(value)
      }

  /**
   * Formats a value for display in the picker.
   *
   * @param value value to format
   */
  fun format(value: Int): String = if (this == YEAR) "$value" else value.toString().padStart(2, '0')
}

/**
 * Displays a picker for selecting a date. The dates go from ( Today ±100 ).
 *
 * @param onDateSelected called with the selected year, month, and day
 */
@Composable
fun DisplayScrollableDatePicker(
    modifier: Modifier = Modifier,
    initialDate: LocalDate = LocalDate.now(),
    onDateSelected: (year: Int, month: Int, day: Int) -> Unit = { _, _, _ -> },
) {
  var date by remember { mutableStateOf(initialDate) } // The default date is Today
  var openField by remember { mutableStateOf<DateField?>(null) }

  // Displays the date field (c.f year, month, and day)
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .border(
                  width = BORDER_THICKNESS,
                  color = lightGray,
                  shape = RoundedCornerShape(size = BIG_ROUND_CORNER),
              )
              .clip(shape = RoundedCornerShape(size = BIG_ROUND_CORNER))
              .padding(PADDING_SMALL),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    DateField.entries.forEach { field ->
      DateBox(
          field = field,
          value = field.format(field.valueIn(date)),
          onClick = { openField = field },
          modifier = Modifier.weight(1f).testTag(DatePickerTestTags.box(field)),
      )
    }
  }

  // Displayed a window when a date field is clicked.
  // This window allows you to select a value for the field in question
  openField?.let { field ->
    val range = field.range(date)
    ScrollablePickerDialog(
        field = field,
        items = range.map(field::format),
        initialIndex = field.valueIn(date) - range.first,
        onDismiss = { openField = null },
        onSelected = { index ->
          date = field.applyTo(date, range.first + index)
          onDateSelected(date.year, date.monthValue, date.dayOfMonth)
          openField = null
        },
    )
  }
}

/**
 * Displays one date field in the picker.
 *
 * @param field current dataField (c.f. year, month or day)
 * @param value formatted field value
 * @param onClick called when the field is selected
 * @param modifier modifier applied to the field
 */
@Composable
private fun DateBox(
    field: DateField,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()

  Column(
      modifier =
          modifier
              .fillMaxWidth()
              .clip(shape = RoundedCornerShape(size = BIG_ROUND_CORNER))
              .background(if (isPressed) lightGray else Color.Transparent)
              .clickable(
                  interactionSource = interactionSource,
                  indication = null,
                  onClick = onClick,
              ),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(PADDING_SMALL),
  ) {
    Text(text = field.label, color = Color.Black)

    HorizontalDivider(
        modifier = Modifier.testTag(DatePickerTestTags.DIVIDER),
        thickness = DIVIDER_THICKNESS,
        color = lightGray,
    )
    Text(
        modifier = Modifier.testTag(DatePickerTestTags.boxValue(field)),
        text = value,
        color = Color.Black,
    )
  }
}

/**
 * Displays a dialog for selecting one value from a scrollable list.
 *
 * @param field title display at the top
 * @param items values shown in the picker
 * @param initialIndex initially selected item index
 * @param onDismiss called when the dialog is dismissed
 * @param onSelected called with the selected item index
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
    val listHeight = screenHeight * 0.3f
    val itemHeight = listHeight / VISIBLE_ITEMS

    Column(
        modifier =
            Modifier.fillMaxWidth(fraction = 0.7f)
                .testTag(DIALOG)
                .background(
                    color = Color.White,
                    shape = RoundedCornerShape(size = BIG_ROUND_CORNER),
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
          color = Color.Black,
      )

      Box(
          modifier =
              Modifier.height(listHeight)
                  .border(
                      width = BORDER_THICKNESS,
                      color = Color.LightGray,
                      shape = RoundedCornerShape(BIG_ROUND_CORNER),
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
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.Black else darkGray,
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
            modifier = Modifier.weight(1f).testTag(DONE_BUTTON),
            onClick = { onSelected(selectedIndex) },
            colors = ButtonDefaults.buttonColors(containerColor = lightOrange),
        ) {
          Text(
              text = "Done",
              color = Color.Black,
              style = MaterialTheme.typography.labelLarge,
          )
        }
        // Cancel Button
        Button(
            modifier = Modifier.weight(1f).testTag(CANCEL_BUTTON),
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = lightOrange),
        ) {
          Text(
              text = "Cancel",
              color = Color.Black,
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
