// Written by YANG Yuhan, with assistance from
// Cursor.
package com.github.se.amber.ui.patient_side

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.github.se.amber.ui.theme.SampleAppTheme
import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random

@Composable
fun PatientHomeFlow(
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    posts: List<PatientMemoryPost> = samplePatientMemories(today),
) {
  if (posts.isEmpty()) return
  var featuredId by remember(today, posts) { mutableStateOf(posts.first().id) }
  var openedId by remember(today, posts) { mutableStateOf<String?>(null) }
  var visibleMonth by remember(today) { mutableStateOf(YearMonth.from(today)) }
  var reactions by remember { mutableStateOf(emptyList<JournalReaction>()) }
  var showRelated by remember { mutableStateOf(false) }
  var showingJournal by remember { mutableStateOf(false) }
  var creatingJournal by remember { mutableStateOf(false) }
  var chosenPrompt by remember { mutableStateOf<String?>(null) }
  var detailFromJournal by remember { mutableStateOf(false) }
  val featured = posts.first { it.id == featuredId }
  val opened = posts.find { it.id == openedId }
  val relatedPosts =
      if (showRelated && opened != null) posts.filter { it.id != opened.id } else emptyList()
  val journalSelected = (showingJournal && opened == null) || (detailFromJournal && opened != null)

  fun goHome() {
    showingJournal = false
    creatingJournal = false
    detailFromJournal = false
    openedId = null
  }

  fun goJournal() {
    showingJournal = true
    creatingJournal = false
    detailFromJournal = false
    openedId = null
  }

  fun openDetail(id: String, fromJournal: Boolean) {
    if (id != featured.id) showRelated = false
    openedId = id
    detailFromJournal = fromJournal
  }

  fun showMore(postId: String) {
    reactions = noteShowMore(postId, reactions)
    showRelated = true
    openDetail(postId, fromJournal = false)
  }

  fun showLess(postId: String) {
    val (nextId, nextReactions) = noteShowLess(postId, posts, Random.nextInt(posts.size), reactions)
    reactions = nextReactions
    showRelated = false
    if (openedId == null) featuredId = nextId else openedId = nextId
  }

  if (opened != null) {
    PatientMemoryDetailScreen(
        post = opened,
        relatedPosts = relatedPosts,
        onBack = { openedId = null },
        onShowMore = { showMore(opened.id) },
        onShowLess = { showLess(opened.id) },
        onOpenRelated = { id ->
          openedId = id
          showRelated = false
        },
        onHome = { goHome() },
        onJournal = { goJournal() },
        homeSelected = !journalSelected,
        modifier = modifier,
    )
  } else if (showingJournal && creatingJournal) {
    PatientJournalCreateScreen(
        chosenPrompt = chosenPrompt,
        onChoosePrompt = { chosenPrompt = it },
        onBack = { creatingJournal = false },
        onHome = { goHome() },
        onJournal = { goJournal() },
        modifier = modifier,
    )
  } else if (showingJournal) {
    PatientJournalHomeScreen(
        pastJournals = posts,
        onCreate = { prompt ->
          chosenPrompt = prompt
          creatingJournal = true
        },
        onOpenJournal = { openDetail(it, fromJournal = true) },
        onHome = { goHome() },
        onJournal = { goJournal() },
        modifier = modifier,
    )
  } else {
    PatientHomeScreen(
        post = featured,
        visibleMonth = visibleMonth,
        markedDates = markedDates(today, posts),
        onOpen = { openDetail(featured.id, fromJournal = false) },
        onRefresh = {
          featuredId = anotherJournal(featured.id, posts, Random.nextInt(posts.size))
          showRelated = false
        },
        onShowMore = { showMore(featured.id) },
        onShowLess = { showLess(featured.id) },
        onMonthChange = { visibleMonth = it },
        onDayClick = { date ->
          val id = journalOn(date, today, posts)?.id
          if (id != null) openDetail(id, fromJournal = false)
        },
        onHome = { goHome() },
        onJournal = { goJournal() },
        modifier = modifier,
    )
  }
}

@Preview(showBackground = true, widthDp = 375, heightDp = 812, name = "Patient home flow")
@Composable
private fun PatientHomeFlowPreview() {
  SampleAppTheme { PatientHomeFlow(today = LocalDate.of(2026, 10, 7)) }
}
