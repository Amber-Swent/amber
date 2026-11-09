// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.ui.utils

import android.content.Context
import android.content.res.Resources
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class UiTextTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val resources: Resources = ApplicationProvider.getApplicationContext<Context>().resources

  private fun showInComposition(text: UiText) {
    composeTestRule.setContent {
      Text(text.asString(LocalResources.current), modifier = Modifier.testTag(TEXT_TAG))
    }
  }

  @Test
  fun resourceWithoutArgsResolvesTheStringResource() {
    val text = UiText.Resource(R.string.title_activity_main)

    assertEquals(resources.getString(R.string.title_activity_main), text.asString(resources))
  }

  @Test
  fun resourceResolvesInComposition() {
    showInComposition(UiText.Resource(R.string.title_activity_main))

    composeTestRule
        .onNodeWithTag(TEXT_TAG)
        .assertTextEquals(resources.getString(R.string.title_activity_main))
  }

  @Test
  fun resourceWithArgsPassesTheArgsInOrder() {
    val mockResources = mockk<Resources>()
    every { mockResources.getString(RESOURCE_ID, "Ana", 3) } returns "Ana has 3 pictures"

    val text = UiText.Resource(RESOURCE_ID, listOf("Ana", 3))

    assertEquals("Ana has 3 pictures", text.asString(mockResources))
  }

  @Test
  @Config(qualifiers = "fr")
  fun dynamicIsShownUnchangedInAnyLanguage() {
    assertEquals("Mamie Jo", UiText.Dynamic("Mamie Jo").asString(resources))
  }

  @Test
  fun dynamicResolvesInComposition() {
    showInComposition(UiText.Dynamic("Mamie Jo"))

    composeTestRule.onNodeWithTag(TEXT_TAG).assertTextEquals("Mamie Jo")
  }

  @Test
  fun resourcesWithTheSameIdAndArgsAreEqual() {
    assertEquals(
        UiText.Resource(R.string.title_activity_main, listOf(1)),
        UiText.Resource(R.string.title_activity_main, listOf(1)),
    )
    assertNotEquals(
        UiText.Resource(R.string.title_activity_main, listOf(1)),
        UiText.Resource(R.string.title_activity_main, listOf(2)),
    )
  }

  @Test
  fun propertiesExposeWhatTheViewModelSet() {
    val resource = UiText.Resource(RESOURCE_ID, listOf("Ana"))

    assertEquals(RESOURCE_ID, resource.id)
    assertEquals(listOf("Ana"), resource.args)
    assertEquals("Mamie Jo", UiText.Dynamic("Mamie Jo").value)
  }

  private companion object {
    const val TEXT_TAG = "ui_text_test_text"
    const val RESOURCE_ID = 42
  }
}
