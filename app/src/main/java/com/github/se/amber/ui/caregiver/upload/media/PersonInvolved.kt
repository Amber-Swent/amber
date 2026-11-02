/* Written by LaMaier, with assistance from
 * Claude (Anthropic) via Claude Code.
 * Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
 * Modified by MeiliV, with assistance from OpenAI Codex.
 * Moved by LaMaier, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload.media

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.BORDER_THICKNESS
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.theme.ROUND_CORNER
import com.github.se.amber.ui.theme.WEIGHT_ONE

/** Tags owned by the person selector helper. */
object DisplayWhoIsInvolvedTestTags {
  const val SELECT_PERSON_INVOLVED = "display_who_is_involved_selected_person_involved"
  const val ADD_PERSON = "display_who_is_involved_add_person"
  const val NO_PERSON_INVOLVED = "display_who_is_involved_no_person_involved"
  private const val PERSON_INVOLVED = "display_who_is_involved_person_involved"

  fun personInvolved(id: String): String = "${PERSON_INVOLVED}_$id"
}

/**
 * Displays selectable chips for people who are involved in a media (picture, audio...). Displays a
 * button that allows the caregiver to add a person to be associated to this media: This person does
 * not have a userId, will be added to the care circle and will be an Instance of `Person`.
 *
 * @param options The available person IDs and labels.
 * @param shownIds The IDs of the people currently selected.
 * @param toggleShownId The callback used to select or deselect a person.
 * @param onAddPerson The callback invoked when the caregiver wants to add a new person.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayWhoIsInvolved(
    options: Map<String, String> = emptyMap(), // id -> label
    shownIds: Set<String> = emptySet(),
    toggleShownId: (String) -> Unit = {},
    onAddPerson: () -> Unit = {},
) {
  Column {
    Text(
        "Who is involved - optional",
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.bodyMedium,
    )
    if (options.isEmpty()) {
      Row(
          modifier =
              Modifier.fillMaxWidth()
                  .height(IntrinsicSize.Min) // Takes the height of its tallest child
                  .testTag(DisplayWhoIsInvolvedTestTags.SELECT_PERSON_INVOLVED),
          horizontalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
            modifier =
                Modifier.weight(WEIGHT_ONE)
                    .testTag(DisplayWhoIsInvolvedTestTags.NO_PERSON_INVOLVED)
                    .border(
                        width = BORDER_THICKNESS,
                        color = MaterialTheme.colorScheme.tertiary,
                        shape = RoundedCornerShape(ROUND_CORNER),
                    )
                    .clip(RoundedCornerShape(ROUND_CORNER))
                    .padding(CAREGIVER_PADDING_MEDIUM),
            contentAlignment = Alignment.Center,
        ) {
          Text(
              text = "No one is part of your care circle",
              color = MaterialTheme.colorScheme.onBackground,
              style = MaterialTheme.typography.bodyMedium,
          )
        }
        // Display a button to add another person to the care circle
        AssistChip(
            onClick = { onAddPerson() },
            label = { Text("+", style = MaterialTheme.typography.labelMedium) },
            modifier = Modifier.fillMaxHeight().testTag(DisplayWhoIsInvolvedTestTags.ADD_PERSON),
            colors =
                AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    labelColor = MaterialTheme.colorScheme.onTertiary,
                ),
        )
      }
    } else {
      FlowRow(
          horizontalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL),
          modifier =
              Modifier.fillMaxWidth().testTag(DisplayWhoIsInvolvedTestTags.SELECT_PERSON_INVOLVED),
      ) {
        // Display a chip for each person associated to the media
        options.forEach { (id, label) ->
          FilterChip(
              selected = id in shownIds,
              onClick = { toggleShownId(id) },
              label = { Text(label, style = MaterialTheme.typography.labelMedium) },
              modifier = Modifier.testTag(DisplayWhoIsInvolvedTestTags.personInvolved(id)),
              colors = selectableChipColors(),
          )
        }
        AssistChip(
            onClick = { onAddPerson() },
            label = { Text("+", style = MaterialTheme.typography.labelMedium) },
            modifier = Modifier.testTag(DisplayWhoIsInvolvedTestTags.ADD_PERSON),
            colors =
                AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    labelColor = MaterialTheme.colorScheme.onTertiary,
                ),
        )
      }
    }
  }
}

/** Colors of the selectable chips: the theme's tertiary color, and its secondary once selected. */
@Composable
internal fun selectableChipColors(): SelectableChipColors =
    FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.tertiary,
        labelColor = MaterialTheme.colorScheme.onTertiary,
        selectedContainerColor = MaterialTheme.colorScheme.secondary,
        selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
    )

/** Preview of [DisplayWhoIsInvolved] using the Amber application theme. */
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun DisplayWhoIsInvolvedPreview() {

  AmberAppTheme(dynamicColor = false) { DisplayWhoIsInvolved() }
}
