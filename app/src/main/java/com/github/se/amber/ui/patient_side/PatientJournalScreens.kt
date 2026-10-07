// Written by YANG Yuhan, with assistance from
// Cursor.
package com.github.se.amber.ui.patient_side

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.se.amber.R
import com.github.se.amber.ui.theme.SampleAppTheme
import com.github.se.amber.ui.theme.backOrange
import com.github.se.amber.ui.theme.darkOrange
import com.github.se.amber.ui.theme.lightOrange

private const val JournalHomePrompt = "What was a dream you have to let go？"

val journalIdeas =
    listOf(
        "What was a dream you have to let go？",
        "Looking back, is there anything you would tell your younger self？",
        "What’s your dream at the age of 8？",
    )

@Composable
fun PatientJournalHomeScreen(
    pastJournals: List<PatientMemoryPost>,
    onCreate: (String?) -> Unit,
    onOpenJournal: (String) -> Unit,
    onHome: () -> Unit,
    onJournal: () -> Unit,
    modifier: Modifier = Modifier,
) {
  JournalScaffold(
      title = "Memory Journal",
      onBack = null,
      onHome = onHome,
      onJournal = onJournal,
      showSearch = true,
      scroll = false,
      modifier = modifier,
  ) {
    SectionTitle("Create a new journal entry", painterResource(R.drawable.ic_journal_create))
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth().weight(1f),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      JournalCard(
          text = JournalHomePrompt,
          modifier = Modifier.weight(1f).clickable { onCreate(journalIdeas.first()) },
      )
      JournalCard(
          text = "+",
          large = true,
          modifier = Modifier.weight(1f).clickable { onCreate(null) },
      )
    }
    Spacer(Modifier.height(16.dp))
    SectionTitle("Past Journals", painterResource(R.drawable.ic_past_journals))
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth().weight(1f),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      pastJournals.take(2).forEach { journal ->
        JournalCard(
            text = journal.topic,
            modifier = Modifier.weight(1f).clickable { onOpenJournal(journal.id) },
        )
      }
    }
  }
}

@Composable
fun PatientJournalCreateScreen(
    chosenPrompt: String?,
    onChoosePrompt: (String) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onJournal: () -> Unit,
    modifier: Modifier = Modifier,
) {
  JournalScaffold(
      title = "Memory Journal- Create",
      titleSize = 20.sp,
      onBack = onBack,
      onHome = onHome,
      onJournal = onJournal,
      showSearch = false,
      scroll = false,
      modifier = modifier,
  ) {
    SectionTitle("Some ideas to think about", painterResource(R.drawable.ic_journal_idea))
    journalIdeas.forEach { idea ->
      val chosen = idea == chosenPrompt
      Spacer(Modifier.weight(0.35f))
      Box(
          modifier =
              Modifier.fillMaxWidth()
                  .weight(1f)
                  .background(backOrange, RoundedCornerShape(16.dp))
                  .border(
                      width = if (chosen) 2.dp else 0.dp,
                      color = if (chosen) darkOrange else Color.Transparent,
                      shape = RoundedCornerShape(16.dp),
                  )
                  .clickable { onChoosePrompt(idea) }
                  .padding(horizontal = 16.dp),
          contentAlignment = Alignment.Center,
      ) {
        Text(
            text = idea,
            color = darkOrange,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
      }
    }
    Spacer(Modifier.weight(0.5f))
    SectionTitle(
        "Ready to create",
        painterResource(R.drawable.ic_ready_to_create),
        iconSize = 28.dp,
    )
    listOf(
            "Upload Text" to Icons.Outlined.Description,
            "Upload Picture" to Icons.Outlined.PhotoCamera,
            "Upload Audio" to Icons.Outlined.Mic,
        )
        .forEach { (label, icon) ->
          Spacer(Modifier.weight(0.35f))
          UploadChoice(label, icon, Modifier.fillMaxWidth().weight(1f))
        }
  }
}

@Composable
private fun JournalScaffold(
    title: String,
    onBack: (() -> Unit)?,
    onHome: () -> Unit,
    onJournal: () -> Unit,
    modifier: Modifier = Modifier,
    titleSize: TextUnit = 24.sp,
    showSearch: Boolean = true,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
  Scaffold(
      modifier = modifier.fillMaxSize(),
      containerColor = Color.White,
      bottomBar = {
        PatientBottomBar(onHome = onHome, onJournal = onJournal, homeSelected = false)
      },
  ) { innerPadding ->
    Column(Modifier.fillMaxSize().padding(innerPadding).background(Color.White)) {
      Row(
          modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        if (onBack != null) {
          Icon(
              Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = "Back",
              tint = darkOrange,
              modifier = Modifier.size(40.dp).clickable(onClick = onBack).padding(8.dp),
          )
        }
        Icon(
            Icons.AutoMirrored.Outlined.MenuBook,
            contentDescription = null,
            tint = darkOrange,
            modifier = Modifier.padding(start = 8.dp).size(22.dp),
        )
        Text(
            text = title,
            color = darkOrange,
            fontSize = titleSize,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp),
        )
      }
      PatientSectionDivider()
      Column(
          Modifier.fillMaxWidth()
              .weight(1f)
              .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
              .padding(horizontal = 16.dp),
      ) {
        Spacer(Modifier.height(12.dp))
        if (showSearch) PatientSearchBar()
        if (showSearch) Spacer(Modifier.height(16.dp))
        content()
        Spacer(Modifier.height(12.dp))
      }
    }
  }
}

@Composable
private fun SectionTitle(text: String, icon: Painter, iconSize: Dp = 24.dp) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(text = text, color = darkOrange, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    Icon(
        icon,
        contentDescription = null,
        tint = Color.Unspecified,
        modifier = Modifier.size(iconSize),
    )
  }
}

@Composable
private fun JournalCard(text: String, modifier: Modifier = Modifier, large: Boolean = false) {
  Box(
      modifier =
          modifier.fillMaxHeight().background(backOrange, RoundedCornerShape(12.dp)).padding(16.dp),
      contentAlignment = Alignment.Center,
  ) {
    Text(
        text = text,
        color = darkOrange,
        fontSize = if (large) 72.sp else 16.sp,
        fontWeight = if (large) FontWeight.Normal else FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun UploadChoice(label: String, icon: ImageVector, modifier: Modifier = Modifier) {
  Row(
      modifier =
          modifier.background(lightOrange, RoundedCornerShape(8.dp)).padding(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
  ) {
    Text(
        text = label,
        color = darkOrange,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.size(24.dp))
    Icon(icon, contentDescription = null, tint = darkOrange, modifier = Modifier.size(24.dp))
  }
}

@Preview(showBackground = true, widthDp = 375, heightDp = 812, name = "Journal home")
@Composable
private fun PatientJournalHomePreview() {
  SampleAppTheme {
    PatientJournalHomeScreen(
        pastJournals = samplePatientMemories(java.time.LocalDate.of(2026, 10, 7)),
        onCreate = {},
        onOpenJournal = {},
        onHome = {},
        onJournal = {},
    )
  }
}

@Preview(showBackground = true, widthDp = 375, heightDp = 812, name = "Journal create")
@Composable
private fun PatientJournalCreatePreview() {
  SampleAppTheme {
    PatientJournalCreateScreen(
        chosenPrompt = journalIdeas.first(),
        onChoosePrompt = {},
        onBack = {},
        onHome = {},
        onJournal = {},
    )
  }
}
