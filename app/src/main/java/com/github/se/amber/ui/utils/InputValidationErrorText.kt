// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.ui.utils

import androidx.annotation.StringRes
import com.github.se.amber.R
import com.github.se.amber.model.validation.InputError
import com.github.se.amber.model.validation.InputRule

/*
 * The UI half of model/validation/InputValidation.kt.
 *
 * Validation is split in two on purpose:
 * - InputValidation (model layer) decides WHAT is wrong with a value: InputRule.visibleError returns
 *   an InputError (BLANK, TOO_SHORT, TOO_LONG, WRONG_FORMAT). It has no Android or UI dependency,
 *   so it stays a plain Kotlin model that any layer can use and test without Robolectric.
 * - This file (UI layer) decides HOW to say it: it turns that InputError into a UiText backed by
 *   strings.xml, so the message shows in the app language.
 *
 * Putting errorText inside InputValidation would make the model import R and UiText, i.e. depend
 * on the UI layer, which goes against the MVVM layering in AGENTS.md.
 */

/**
 * The message to show under a text field, in the app language, or null to show nothing.
 *
 * It shows the same errors as [InputRule.visibleError], as a [UiText] the screen resolves with
 * `asString(LocalResources.current)`. The lengths come from the rule, so a limit changed in
 * `InputValidation` changes the message too.
 *
 * Example, in a ViewModel's UI state:
 * ```
 * val nameError: UiText?
 *   get() = InputValidation.NAME.errorText(
 *       name,
 *       submitted = validationAttempted,
 *       blankRes = R.string.createProfileFillOut,
 *       formatRes = R.string.inputErrorNameFormat,
 *   )
 * ```
 *
 * @param text The input as typed.
 * @param submitted Whether the user has tried to save what they typed. Before that, only errors
 *   that typing more can't fix are shown (a character the field doesn't allow, a text too long).
 * @param blankRes The message shown when a required field is left empty, once the user tries to
 *   save.
 * @param formatRes The message shown when the input has a character or shape the field doesn't
 *   allow. Pass the key that matches the rule, e.g. [R.string.inputErrorNameFormat] for a name; the
 *   default, [R.string.inputErrorFormat], only says that some characters aren't allowed.
 */
fun InputRule.errorText(
    text: String,
    submitted: Boolean = true,
    @StringRes blankRes: Int? = R.string.inputErrorBlank,
    @StringRes formatRes: Int = R.string.inputErrorFormat,
): UiText? =
    when (visibleError(text, submitted)) {
      null -> null
      InputError.BLANK -> blankRes?.let { UiText.Resource(it) }
      InputError.TOO_SHORT ->
          UiText.Plural(
              if (minLength == maxLength) R.plurals.inputErrorIncomplete
              else R.plurals.inputErrorTooShort,
              minLength,
          )
      InputError.TOO_LONG -> UiText.Plural(R.plurals.inputErrorTooLong, maxLength)
      InputError.WRONG_FORMAT -> UiText.Resource(formatRes)
    }
