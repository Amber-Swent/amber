// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.
// Modified by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.caregiver.upload.media

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.github.se.amber.ui.theme.BORDER_THICKNESS
import com.github.se.amber.ui.theme.DATE_PICKER_LIST_HEIGHT_FRACTION
import com.github.se.amber.ui.theme.MAX_VISIBLE_ITEM
import com.github.se.amber.ui.theme.ROUND_CORNER
import com.github.se.amber.ui.utils.PopupDialog

/**
 * Displays the choices for one [DateField] in a snap-scrolling popup.
 *
 * The first visible row is the current selection. Tapping a row selects it immediately; the Done
 * action selects the first visible row. The caller handles closing the popup after selection.
 *
 * @param field date component being edited; its label appears in the popup title.
 * @param items choices displayed in list order.
 * @param initialIndex index shown when the popup opens.
 * @param onDismiss called when the popup is dismissed or its close button is pressed.
 * @param onSelected called with the selected index, either from a row tap or Done.
 */
@Composable
internal fun ScrollablePickerDialog(
    field: DateField,
    items: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit = {},
    onSelected: (Int) -> Unit = {},
) {
  val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
  val snapFling = rememberSnapFlingBehavior(listState)
  val selectedIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
  val screenHeight =
      with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
  val listHeight = screenHeight * DATE_PICKER_LIST_HEIGHT_FRACTION
  val itemHeight = listHeight / MAX_VISIBLE_ITEM

  PopupDialog(
      title = "Select ${field.label}",
      onDismiss = onDismiss,
      actionText = "Done",
      onAction = { onSelected(selectedIndex) },
      dialogTestTag = DatePickerTestTags.DIALOG,
      titleTestTag = DatePickerTestTags.dialogTitle(field),
      dismissTestTag = DatePickerTestTags.DISMISS_BUTTON,
      actionTestTag = DatePickerTestTags.DONE_BUTTON,
  ) {
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
          contentPadding = PaddingValues(vertical = (listHeight - itemHeight) / 2),
          modifier = Modifier.fillMaxWidth().testTag(DatePickerTestTags.DIALOG_LIST),
      ) {
        itemsIndexed(items) { index, item ->
          val isSelected = index == selectedIndex
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
  }
}
