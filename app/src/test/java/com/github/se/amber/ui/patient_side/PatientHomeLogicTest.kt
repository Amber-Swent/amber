// Written by YANG Yuhan, with assistance from
// Cursor.
package com.github.se.amber.ui.patient_side

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PatientHomeLogicTest {
  private val today = LocalDate.of(2026, 10, 7)
  private val posts = samplePatientMemories(today)

  @Test
  fun onlyACircledDayInTheNext30DaysOpensThatJournal() {
    val anniversary = today.plusDays(3)
    assertEquals(posts[0].onDate.plusYears(28), anniversary)
    assertEquals(posts[0], journalOn(anniversary, today, posts))
    assertNull(journalOn(posts[0].onDate, today, posts))
    assertNull(journalOn(today.plusDays(1), today, posts))
    assertNull(journalOn(today.plusDays(30), today, posts))
  }

  @Test
  fun showMoreRecordsTheChoiceAndKeepsTheJournal() {
    val reactions = noteShowMore(posts[0].id, emptyList())

    assertEquals(listOf(JournalReaction(posts[0].id, wantsMore = true)), reactions)
  }

  @Test
  fun showLessRecordsTheChoiceAndSwitchesJournal() {
    val (nextId, reactions) = noteShowLess(posts[0].id, posts, choice = 0, emptyList())

    assertEquals(posts[1].id, nextId)
    assertEquals(listOf(JournalReaction(posts[0].id, wantsMore = false)), reactions)
  }
}
