// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.validation

/** Why a text field's value is refused. */
enum class InputError {
  /** Empty, or only spaces, in a required field. */
  BLANK,

  /** Shorter than the field needs, e.g. an invitation code still being typed. */
  TOO_SHORT,

  /** Longer than the field allows. */
  TOO_LONG,

  /** Contains characters, or has a shape, the field doesn't allow. */
  WRONG_FORMAT,
}

/**
 * The limits and format of one kind of text field. Every field the user types in has one, so that a
 * screen never writes its own checks: a new field reuses one of [InputValidation]'s rules, or
 * declares its own there.
 *
 * A ViewModel uses a rule in two places:
 * - [clean] on every keystroke: it cuts the text at [maxLength] (so typing stops there), removes
 *   invisible control characters, which a user can only paste, and applies [removeSpaces] and
 *   [uppercase].
 * - [check] or [errorMessage] before saving: they trim the text, then say why it is refused, or
 *   return null if it is fine. A text of only spaces counts as blank.
 *
 * @param maxLength The most characters the field holds.
 * @param minLength The fewest characters a nonblank value needs.
 * @param required Whether a blank value is refused; false for an optional field.
 * @param multiLine Whether the text may hold line breaks, e.g. a description.
 * @param allowedCharacters The characters the field can hold, or null for any but control ones.
 *   Checked while typing, so a wrong character shows its error at once.
 * @param format The shape the whole trimmed value must have, or null for any.
 * @param formatMessage Shown when the value has a character or shape the field doesn't allow.
 * @param removeSpaces Whether [clean] removes every space, for values that never have any.
 * @param uppercase Whether [clean] puts the text in capitals.
 */
data class InputRule(
    val maxLength: Int,
    val minLength: Int = 1,
    val required: Boolean = true,
    val multiLine: Boolean = false,
    val allowedCharacters: Regex? = null,
    val format: Regex? = null,
    val formatMessage: String = "This has characters that aren't allowed",
    val removeSpaces: Boolean = false,
    val uppercase: Boolean = false,
) {
  init {
    require(minLength in 1..maxLength) { "Need 1 <= minLength <= maxLength" }
  }

  /** The same rule, but for a field the user may leave blank. */
  fun optional(): InputRule = copy(required = false)

  /** [text] as the field should hold it after a keystroke or a paste. */
  fun clean(text: String): String {
    var cleaned = if (multiLine) text.replace("\r\n", "\n") else text
    cleaned = cleaned.filterNot {
      (it.isISOControl() && !(multiLine && it == '\n')) || (removeSpaces && it.isWhitespace())
    }
    if (uppercase) cleaned = cleaned.uppercase()
    return limit(cleaned, maxLength)
  }

  /** Why [text] can't be saved, or null if it can; it is trimmed first. */
  fun check(text: String): InputError? {
    val value = text.trim()
    return when {
      value.isEmpty() -> if (required) InputError.BLANK else null
      value.length > maxLength -> InputError.TOO_LONG
      !hasOnlyAllowedCharacters(value) -> InputError.WRONG_FORMAT
      value.length < minLength -> InputError.TOO_SHORT
      format?.matches(value) == false -> InputError.WRONG_FORMAT
      else -> null
    }
  }

  /**
   * Why [text] is refused, as a message to show under the field, or null to show nothing.
   *
   * While the user is still typing ([submitted] false), only what can't be fixed by typing more is
   * shown: a character the field doesn't allow, or a text too long. Once they tried to save, a
   * blank or too short value is shown too.
   *
   * @param text The value as typed.
   * @param submitted Whether the user has tried to save the value.
   * @param blankMessage Shown for a blank required value, or null to show nothing, e.g. when the
   *   save button is disabled anyway.
   */
  fun errorMessage(
      text: String,
      submitted: Boolean = true,
      blankMessage: String? = "Please fill this out",
  ): String? {
    return when (check(text) ?: return null) {
      InputError.BLANK -> blankMessage?.takeIf { submitted }
      InputError.TOO_SHORT -> "Use at least $minLength characters".takeIf { submitted }
      InputError.TOO_LONG -> "Use at most $maxLength characters"
      InputError.WRONG_FORMAT ->
          formatMessage.takeIf { submitted || !hasOnlyAllowedCharacters(text.trim()) }
    }
  }

  /** Whether the value can be saved as it is. */
  fun isValid(text: String): Boolean = check(text) == null

  private fun hasOnlyAllowedCharacters(value: String): Boolean =
      value.none { it.isISOControl() && !(multiLine && it == '\n') } &&
          (allowedCharacters == null || allowedCharacters.matches(value))
}

/**
 * The rules of every text field in the app, kept in one place so that screens don't repeat them. A
 * field for a new feature reuses the matching rule (e.g. [NAME] for any person's name) or adds its
 * own here, with its limits next to the others.
 */
object InputValidation {
  // Lengths as constants, for the UI to show them (see theme/Constant.kt)
  const val NAME_MAX_LENGTH = 50
  const val SHORT_TEXT_MAX_LENGTH = 50
  const val DESCRIPTION_MAX_LENGTH = 200
  const val PLACE_QUERY_MAX_LENGTH = 100
  // The longest address SMTP allows (RFC 5321)
  const val EMAIL_MAX_LENGTH = 254

  // Must match the codes FirebaseInvitationCreationRepository generates: no I, O, 0 or 1, which
  // are easy to mix up when read aloud or copied by hand
  const val INVITATION_CODE_LENGTH = 8
  const val INVITATION_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

  /**
   * A person's first name, surname or nickname: letters in any alphabet, with their accents
   * (\p{M}), as words joined by spaces, hyphens or apostrophes (straight, or the curly one many
   * keyboards type): "Anne-Marie", "D'Angelo", "Jean Paul". No digits, emojis or other symbols. Use
   * [InputRule.optional] for a name that may be left blank.
   */
  val NAME =
      InputRule(
          maxLength = NAME_MAX_LENGTH,
          allowedCharacters = Regex("[\\p{L}\\p{M} '’-]*"),
          format = Regex("[\\p{L}\\p{M}]+(?:[ '’-]+[\\p{L}\\p{M}]+)*"),
          formatMessage = "Use only letters, spaces, hyphens (-) and apostrophes (')",
      )

  /** A short title on one line, like a care circle's name: any characters but control ones. */
  val SHORT_TEXT = InputRule(maxLength = SHORT_TEXT_MAX_LENGTH)

  /** A media description: any characters but control ones, on several lines. */
  val DESCRIPTION = InputRule(maxLength = DESCRIPTION_MAX_LENGTH, multiLine = true)

  /** A place typed to be searched for. */
  val PLACE_QUERY = InputRule(maxLength = PLACE_QUERY_MAX_LENGTH, required = false)

  /** An email address, of the same shape as android.util.Patterns.EMAIL_ADDRESS accepts. */
  val EMAIL =
      InputRule(
          maxLength = EMAIL_MAX_LENGTH,
          // Patterns.EMAIL_ADDRESS itself doesn't exist in local unit tests, where rules are tested
          format =
              Regex(
                  "[a-zA-Z0-9+._%\\-]{1,256}" +
                      "@" +
                      "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
                      "(\\.[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25})+"
              ),
          formatMessage = "Enter a valid email address",
          removeSpaces = true,
      )

  /** An invitation code, in capitals and without spaces, as they are generated. */
  val INVITATION_CODE =
      InputRule(
          maxLength = INVITATION_CODE_LENGTH,
          minLength = INVITATION_CODE_LENGTH,
          allowedCharacters = Regex("[$INVITATION_CODE_ALPHABET]*"),
          formatMessage = "Codes only use capital letters and the digits 2 to 9, without I or O",
          removeSpaces = true,
          uppercase = true,
      )
}

/**
 * Cuts [text] to [maxLength] characters, without splitting an emoji or other character stored as
 * two (a surrogate pair) in half.
 */
private fun limit(text: String, maxLength: Int): String {
  if (text.length <= maxLength) return text
  val cut = text.take(maxLength)
  return if (cut.last().isHighSurrogate()) cut.dropLast(1) else cut
}
