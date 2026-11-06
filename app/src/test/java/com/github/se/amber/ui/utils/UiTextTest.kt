// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.ui.utils

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
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

@RunWith(RobolectricTestRunner::class)
class UiTextTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()

  private fun showInComposition(text: UiText) {
    composeTestRule.setContent { Text(text.asString(), modifier = Modifier.testTag(TEXT_TAG)) }
  }

  @Test
  fun resourceWithoutArgsResolvesTheStringResourceWithContext() {
    val text = UiText.Resource(R.string.title_activity_main)

    assertEquals(context.getString(R.string.title_activity_main), text.asString(context))
  }

  @Test
  fun resourceWithoutArgsResolvesTheStringResourceInComposition() {
    showInComposition(UiText.Resource(R.string.title_activity_main))

    composeTestRule
        .onNodeWithTag(TEXT_TAG)
        .assertTextEquals(context.getString(R.string.title_activity_main))
  }

  @Test
  fun resourceWithArgsPassesTheArgsInOrder() {
    val mockContext = mockk<Context>()
    every { mockContext.getString(RESOURCE_ID, "Ana", 3) } returns "Ana has 3 pictures"

    val text = UiText.Resource(RESOURCE_ID, listOf("Ana", 3))

    assertEquals("Ana has 3 pictures", text.asString(mockContext))
  }

  @Test
  fun dynamicReturnsItsValueUnchangedWithContext() {
    assertEquals("Mamie Jo", UiText.Dynamic("Mamie Jo").asString(context))
  }

  @Test
  fun dynamicReturnsItsValueUnchangedInComposition() {
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

  private companion object {
    const val TEXT_TAG = "ui_text_test_text"
    const val RESOURCE_ID = 42
  }
}
