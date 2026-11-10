// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by LaMaier, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToLog

/**
 * The steps every end-to-end journey takes on the app's screens: wait for a node, click it, type
 * into it, read its text. Each one waits first, since navigation and emulator calls take real time.
 *
 * @param rule the compose rule the journey's app runs in
 * @param timeoutMillis how long each step waits for its node
 */
class E2eSteps(
    private val rule: ComposeContentTestRule,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {

  /**
   * Waits for an enabled node and clicks it.
   *
   * @param tag the test tag of the node to click
   */
  fun click(tag: String) {
    waitForNode(hasTestTag(tag) and isEnabled(), "enabled node '$tag'")
    val node = rule.onNodeWithTag(tag)
    // A control can sit below the emulator window on a scrolling screen. Touch input misses it
    // there, so bring it into view when the screen scrolls.
    try {
      node.performScrollTo()
    } catch (_: AssertionError) {
      // This screen does not scroll. The control is already in view.
    }
    node.performClick()
  }

  /**
   * Waits for an enabled node and enters text into it.
   *
   * @param tag the test tag of the input node
   * @param text the text to enter
   */
  fun typeInto(tag: String, text: String) {
    waitForNode(hasTestTag(tag) and isEnabled(), "enabled node '$tag'")
    rule.onNodeWithTag(tag).performTextInput(text)
  }

  /**
   * Returns the text semantics value of the first node with the given tag.
   *
   * @param tag the test tag of the node to inspect
   * @return the node's text, or null when no matching node or text exists
   */
  fun textOf(tag: String): String? =
      rule
          .onAllNodesWithTag(tag)
          .fetchSemanticsNodes()
          .firstOrNull()
          ?.config
          ?.getOrNull(SemanticsProperties.Text)
          ?.joinToString("")

  /**
   * Waits for exactly one node matching the given semantics matcher.
   *
   * @param matcher the semantics condition that the node must satisfy
   * @param description a human-readable description used in timeout diagnostics
   * @param timeoutMillis how long to wait; the steps' timeout by default
   */
  @OptIn(ExperimentalTestApi::class)
  fun waitForNode(
      matcher: SemanticsMatcher,
      description: String,
      timeoutMillis: Long = this.timeoutMillis,
  ) {
    try {
      rule.waitUntilExactlyOneExists(matcher, timeoutMillis)
    } catch (e: Exception) {
      rule.onRoot().printToLog("E2E")
      throw AssertionError("Timed out waiting for $description", e)
    }
  }

  /**
   * Waits for a node with the given test tag.
   *
   * @param tag the test tag of the node to wait for
   * @param timeoutMillis how long to wait; the steps' timeout by default
   */
  fun waitFor(tag: String, timeoutMillis: Long = this.timeoutMillis) =
      waitForNode(hasTestTag(tag), "node '$tag'", timeoutMillis)

  /** Holds the default wait of each step. */
  companion object {
    /** Each step waits for real navigation and emulator calls. */
    const val DEFAULT_TIMEOUT_MILLIS = 10_000L

    /**
     * Creates a matcher for the editable text value of a semantics node.
     *
     * @param expected the text value the node must contain
     * @return a matcher that checks the node's editable text
     */
    fun hasEditableText(expected: String) =
        SemanticsMatcher("has editable text '$expected'") {
          it.config.getOrNull(SemanticsProperties.EditableText)?.text == expected
        }
  }
}
