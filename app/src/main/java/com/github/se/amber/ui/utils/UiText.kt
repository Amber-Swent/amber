// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.ui.utils

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Text that a ViewModel asks the UI to show.
 *
 * ViewModels have no `Context`, so they can't turn a string resource into text. They return a
 * [UiText] instead, and the UI resolves it in the current app language. A fixed message with no
 * arguments can stay a plain `@StringRes Int`. See "Text and translation" in
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

  /**
   * Resolves the text outside composition, for example for a `Toast` inside `LaunchedEffect`.
   *
   * @param context the context whose resources give the current app language.
   * @return the text to show.
   */
  fun asString(context: Context): String =
      when (this) {
        is Resource -> context.getString(id, *args.toTypedArray())
        is Dynamic -> value
      }
}

/**
 * Resolves the text in composition. It is recomposed when the app language changes.
 *
 * @return the text to show.
 */
@Composable
fun UiText.asString(): String =
    when (this) {
      is UiText.Resource -> stringResource(id, *args.toTypedArray())
      is UiText.Dynamic -> value
    }
