/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver

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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.theme.BORDER_THICKNESS
import com.github.se.amber.ui.theme.DIVIDER_THICKNESS
import com.github.se.amber.ui.theme.PADDING_MEDIUM
import com.github.se.amber.ui.theme.PADDING_SMALL
import com.github.se.amber.ui.theme.ROUND_CORNER
import com.github.se.amber.ui.theme.darkGray
import com.github.se.amber.ui.theme.darkOrange
import com.github.se.amber.ui.theme.lightGray
import com.github.se.amber.ui.theme.lightOrange
import com.github.se.amber.ui.theme.orange
import java.time.LocalDate

/**
 * Displays the title for caregiver's [Screen]
 *
 * @param title title to display
 * @param modifier modifier to apply to the text (can for e.g. contains the necessary TestTag)
 */
@Composable
fun DisplayCaregiverTitle(title: String, modifier: Modifier) {
  Text(
      modifier = modifier.padding(top = PADDING_SMALL, bottom = PADDING_MEDIUM),
      text = title,
      color = orange,
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
  )
}

private const val VISIBLE_ITEMS = 5 // number of rows visible at once in the picker

private enum class DateField(val label: String) {
  YEAR("Year"),
  MONTH("Month"),
  DAY("Day");

  fun range(date: LocalDate): IntRange =
      when (this) {
        YEAR -> date.year - 100..date.year + 100
        MONTH -> 1..12
        DAY -> 1..date.lengthOfMonth()
      }

  fun valueIn(date: LocalDate): Int =
      when (this) {
        YEAR -> date.year
        MONTH -> date.monthValue
        DAY -> date.dayOfMonth
      }

  fun applyTo(date: LocalDate, value: Int): LocalDate =
      when (this) {
        YEAR -> date.withYear(value)
        MONTH -> date.withMonth(value)
        DAY -> date.withDayOfMonth(value)
      }

  // FIXME : line under is dirty...
  fun format(value: Int): String = if (this == YEAR) "$value" else value.toString().padStart(2, '0')
}

@Composable
fun DisplayScrollableDatePicker(
    onDateSelected: (year: Int, month: Int, day: Int) -> Unit = { _, _, _ -> }
) {
  var date by remember { mutableStateOf(LocalDate.now()) } // The default date is Today
  var openField by remember { mutableStateOf<DateField?>(null) }

  Row(
      modifier =
          Modifier.fillMaxWidth()
              .border(
                  width = BORDER_THICKNESS,
                  color = lightGray,
                  shape = RoundedCornerShape(size = ROUND_CORNER),
              )
              .clip(shape = RoundedCornerShape(size = ROUND_CORNER))
              .padding(PADDING_SMALL),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    DateField.entries.forEach { field ->
      DateBox(
          label = field.label,
          value = field.format(field.valueIn(date)),
          onClick = { openField = field },
          modifier = Modifier.weight(1f),
      )
    }
  }

  openField?.let { field ->
    val range = field.range(date)
    ScrollablePickerDialog(
        title = "Select ${field.label}",
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

@Composable
private fun DateBox(
    label: String,
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
              .clip(shape = RoundedCornerShape(size = ROUND_CORNER))
              .background(if (isPressed) lightGray.copy(alpha = 0.4f) else Color.Transparent)
              .clickable(
                  interactionSource = interactionSource,
                  indication = null,
                  onClick = onClick,
              ),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(PADDING_SMALL),
  ) {
    Text(text = label, color = Color.Black)

    HorizontalDivider(
        modifier = Modifier.testTag(BottomNavigationMenuTestTags.DIVIDER),
        thickness = DIVIDER_THICKNESS,
        color = lightGray,
    )
    Text(text = value, color = Color.Black)
  }
}

@Composable
private fun ScrollablePickerDialog(
    title: String,
    items: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit,
) {
  Dialog(
      onDismissRequest = onDismiss,
      properties =
          DialogProperties(
              usePlatformDefaultWidth = false
          ), // lets the dialog use the width we give it
  ) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val listHeight = screenHeight * 0.3f
    val itemHeight = listHeight / VISIBLE_ITEMS // each row = an equal share of the list

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    // Snapping guarantees the list always rests with one item exactly in the
    // highlighted slot, so the first visible item index IS the selected one.
    val snapFling = rememberSnapFlingBehavior(listState)
    val selectedIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    Column(
        modifier =
            Modifier.fillMaxWidth(fraction = 0.7f)
                .background(color = Color.White, shape = RoundedCornerShape(size = ROUND_CORNER))
                .padding(PADDING_MEDIUM),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // display Select [Year/Month/Day]
      Text(
          text = title,
          style = MaterialTheme.typography.titleLarge,
          color = Color.Black,
          modifier = Modifier.padding(bottom = PADDING_MEDIUM),
      )

      Box(
          modifier =
              Modifier.height(listHeight)
                  .border(
                      width = BORDER_THICKNESS,
                      color = Color.LightGray,
                      shape = RoundedCornerShape(ROUND_CORNER),
                  )
      ) {
        LazyColumn(
            state = listState,
            flingBehavior = snapFling,
            contentPadding =
                PaddingValues(
                    vertical = (listHeight - itemHeight) / 2
                ), // Lets the first and last items reach the center slot
            modifier = Modifier.fillMaxWidth(),
        ) {
          itemsIndexed(items) { index, item ->
            val isSelected = index == selectedIndex
            Text(
                text = item,
                style =
                    if (isSelected) MaterialTheme.typography.bodyLarge
                    else MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.Black else darkGray,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier.fillMaxWidth()
                        .height(itemHeight)
                        .clickable { onSelected(index) }
                        .wrapContentHeight(Alignment.CenterVertically),
            )
          }
        }

        // Center highlight
        Box(
            modifier =
                Modifier.align(Alignment.Center).fillMaxWidth().border(BORDER_THICKNESS, darkOrange)
        )
      }

      Row(
          modifier = Modifier.fillMaxWidth().padding(top = PADDING_SMALL),
          horizontalArrangement = Arrangement.spacedBy(PADDING_SMALL),
      ) {
        // TODO : Should extract on a helper function to avoid duplicate
        Button(
            onClick = { onSelected(selectedIndex) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = lightOrange),
        ) {
          Text(
              text = "Done",
              color = Color.Black,
              style = MaterialTheme.typography.labelLarge,
          )
        }

        Button(
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
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
