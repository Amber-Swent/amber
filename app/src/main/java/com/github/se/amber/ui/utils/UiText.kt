// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.ui.utils

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * Text that a ViewModel asks the UI to show.
 *
 * ViewModels have no `Context`, so they can't turn a string resource into text. They return a
 * [UiText] instead, and the UI resolves it in the current app language with [asString]. A fixed
 * message with no arguments can stay a plain `@StringRes Int`. See "Text and translation" in
 * `UI_CONVENTIONS_AGENT.md`.
 */
sealed interface UiText {
  /**
   * App text: a string resource, with its arguments if it has placeholders (`%1$s` for a string,
   * `%1$d` for an integer). It is translated.
   *
   * Example: with `<string name="unknownMember">Unknown member %1$d</string>`,
   * `UiText.Resource(R.string.unknownMember, listOf(3))` shows "Unknown member 3" in English and
   * "Membre inconnu 3" in French.
   *
   * @property id the string resource, defined in `values/strings.xml` and `values-fr/strings.xml`.
   * @property args the values for the placeholders, in order.
   */
  data class Resource(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

  /**
   * App text that changes with a number: a plurals resource. It is translated, and the wording
   * follows the plural rules of the language ("1 character", "3 characters").
   *
   * Example: with `<plurals name="inputErrorTooLong">` holding "Use at most %1$d character" (`one`)
   * and "Use at most %1$d characters" (`other`), `UiText.Plural(R.plurals.inputErrorTooLong, 3)`
   * shows "Use at most 3 characters" in English and "Utilisez au plus 3 caractères" in French.
   *
   * @property id the plurals resource, defined in `values/strings.xml` and `values-fr/strings.xml`.
   * @property count the number that picks the wording.
   * @property args the values for the placeholders, in order. By default, only [count].
   */
  data class Plural(
      @param:PluralsRes val id: Int,
      val count: Int,
      val args: List<Any> = listOf(count),
  ) : UiText

  /**
   * User data shown as is (a name, a nickname) that fills the whole text.
   *
   * Never app text: a literal or a constant here is hardcoded English. Data inside a sentence is an
   * argument of [Resource] instead.
   *
   * Example: `UiText.Dynamic(member.name)` shows "Mamie Jo" in every language. Wrong:
   * `UiText.Dynamic("Welcome, $name")`, which stays English; use a [Resource] with `name` as its
   * argument.
   *
   * @property value the user data to show.
   */
  data class Dynamic(val value: String) : UiText
}

/**
 * Resolves the text in the current app language.
 * - In a composable: `text.asString(LocalResources.current)`, which updates when the language
 *   changes.
 * - In a `LaunchedEffect`, e.g. for a `Toast`: read `val resources = LocalResources.current` in the
 *   composable, then call `text.asString(resources)` inside the effect.
 *
 * @param resources the app resources, which carry the current language.
 * @return the text to show.
 */
fun UiText.asString(resources: Resources): String =
    when (this) {
      is UiText.Resource -> resources.getString(id, *args.toTypedArray())
      is UiText.Plural -> resources.getQuantityString(id, count, *args.toTypedArray())
      is UiText.Dynamic -> value
    }
