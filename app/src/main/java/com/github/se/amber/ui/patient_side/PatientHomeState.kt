// Written by YANG Yuhan, with assistance from
// Cursor.
package com.github.se.amber.ui.patient_side

import java.time.LocalDate

/** A memory shown on the patient home screen. */
data class PatientMemoryPost(
    val id: String,
    val authorName: String,
    val topic: String,
    val summary: String,
    val description: String,
    val onDate: LocalDate,
)

/** Whether this journal made the patient want more. Kept so later analysis can read the choice. */
data class JournalReaction(val postId: String, val wantsMore: Boolean)

private const val UPCOMING_DAY_COUNT = 30

fun samplePatientMemories(today: LocalDate): List<PatientMemoryPost> {
  return listOf(
      PatientMemoryPost(
          id = "garden",
          authorName = "Arthur",
          topic = "The garden in spring",
          summary = "Roses by the kitchen window.",
          description =
              "Arthur kept roses along the kitchen window. On warm mornings he opened it and named the one that smelled the strongest.",
          onDate = today.plusDays(3).minusYears(28),
      ),
      PatientMemoryPost(
          id = "lunch",
          authorName = "Helen",
          topic = "Sunday lunch",
          summary = "The whole family around one table.",
          description =
              "Sunday lunch filled the dining room. Helen saved a seat by the window and passed the bread before anyone asked.",
          onDate = today.plusDays(12).minusYears(15),
      ),
      PatientMemoryPost(
          id = "train",
          authorName = "Marco",
          topic = "The train to the sea",
          summary = "A day trip with the blue suitcase.",
          description =
              "Marco took the morning train to the sea and carried the blue suitcase the whole way. They ate ice cream on the platform coming home.",
          onDate = today.plusDays(24).minusYears(40),
      ),
  )
}

/**
 * The next 30 days include this month and day when [memoryDate] already happened, in any past year.
 */
fun anniversaryInUpcomingDays(memoryDate: LocalDate, today: LocalDate): LocalDate? {
  if (memoryDate.isAfter(today)) return null
  return (0 until UPCOMING_DAY_COUNT)
      .map { today.plusDays(it.toLong()) }
      .find { it.month == memoryDate.month && it.dayOfMonth == memoryDate.dayOfMonth }
}

/** The journal for a circled day, or null when that day has no memory in the next 30 days. */
fun journalOn(
    date: LocalDate,
    today: LocalDate,
    posts: List<PatientMemoryPost>,
): PatientMemoryPost? {
  return posts.find { anniversaryInUpcomingDays(it.onDate, today) == date }
}

fun markedDates(today: LocalDate, posts: List<PatientMemoryPost>): Set<LocalDate> {
  return posts.mapNotNull { anniversaryInUpcomingDays(it.onDate, today) }.toSet()
}

fun anotherJournal(currentId: String, posts: List<PatientMemoryPost>, choice: Int): String {
  val others = posts.filter { it.id != currentId }
  if (others.isEmpty()) return currentId
  return others[choice.mod(others.size)].id
}

fun noteShowMore(postId: String, reactions: List<JournalReaction>): List<JournalReaction> {
  return reactions + JournalReaction(postId, wantsMore = true)
}

fun noteShowLess(
    currentId: String,
    posts: List<PatientMemoryPost>,
    choice: Int,
    reactions: List<JournalReaction>,
): Pair<String, List<JournalReaction>> {
  return anotherJournal(currentId, posts, choice) to
      (reactions + JournalReaction(currentId, wantsMore = false))
}
