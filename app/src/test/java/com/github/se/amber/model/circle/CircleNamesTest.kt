// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.circle

import com.github.se.amber.model.user.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CircleNamesTest {

  private val anne = Person(id = "p1", firstName = "Anne", lastName = "Dupont")
  private val marie = Person(id = "u1", firstName = "Marie", lastName = "Curie", nickname = "Mimi")

  private val circle =
      CareCircle(
          id = "c1",
          memberIds = listOf("u1", "u2"),
          people = mapOf("p1" to anne),
          nicknames = mapOf("u2" to mapOf("p1" to "Granny")),
      )

  @Test
  fun fullNameJoinsFirstAndLastName() {
    assertEquals("Anne Dupont", anne.fullName())
  }

  @Test
  fun fullNameSkipsBlankLastName() {
    assertEquals("Anne", anne.copy(lastName = " ").fullName())
  }

  @Test
  fun taggableIdsContainsMembersAndPeople() {
    assertEquals(setOf("u1", "u2", "p1"), circle.taggableIds())
  }

  @Test
  fun displayNameUsesTheViewerOwnNicknameFirst() {
    assertEquals("Granny", circle.displayNameOf("p1", "u2", emptyMap()))
  }

  @Test
  fun displayNameIgnoresBlankViewerNickname() {
    val blank = circle.copy(nicknames = mapOf("u2" to mapOf("p1" to " ")))
    assertEquals("Anne Dupont", blank.displayNameOf("p1", "u2", emptyMap()))
  }

  @Test
  fun displayNameUsesTheDefaultNicknameOfAMember() {
    assertEquals("Mimi", circle.displayNameOf("u1", "u2", mapOf("u1" to marie)))
  }

  @Test
  fun displayNameFallsBackToFullName() {
    assertEquals("Anne Dupont", circle.displayNameOf("p1", "u1", emptyMap()))
  }

  @Test
  fun displayNameIsNullForUnknownId() {
    assertNull(circle.displayNameOf("u2", "u1", emptyMap()))
  }
}
