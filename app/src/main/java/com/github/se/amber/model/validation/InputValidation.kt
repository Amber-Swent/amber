// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

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
 *   invisible control and formatting characters, which a user can only paste, and applies
 *   [removeSpaces] and [uppercase].
 * - [check] or [errorMessage] before saving: they trim the text, then say why it is refused, or
 *   return null if it is fine. A text of only spaces counts as blank.
 *
 * @param maxLength The most characters the field holds.
 * @param minLength The fewest characters a nonblank value needs.
 * @param required Whether a blank value is refused; false for an optional field.
 * @param multiLine Whether the text may hold line breaks, e.g. a description.
 * @param allowedCharacters The characters the field can hold, or null for any but invisible ones.
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
    cleaned = cleaned.filterNot { isInvisible(it) || (removeSpaces && it.isWhitespace()) }
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
   * The error the field should show now, or null to show nothing.
   *
   * While the user types ([submitted] false), only errors that typing more can't fix are shown: a
   * character the field doesn't allow, and a text that is too long. After the user tries to save,
   * every error is shown. The UI turns the error into a message in the app language (see
   * `ui/utils/InputValidationErrorText.kt`).
   *
   * @param text The value as typed.
   * @param submitted Whether the user has tried to save the value.
   */
  fun visibleError(text: String, submitted: Boolean = true): InputError? {
    val error = check(text) ?: return null
    val shown =
        when (error) {
          InputError.BLANK,
          InputError.TOO_SHORT -> submitted
          InputError.TOO_LONG -> true
          InputError.WRONG_FORMAT -> submitted || !hasOnlyAllowedCharacters(text.trim())
        }
    return error.takeIf { shown }
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
    return when (visibleError(text, submitted) ?: return null) {
      InputError.BLANK -> blankMessage
      InputError.TOO_SHORT -> tooShortMessage()
      InputError.TOO_LONG -> "Use at most $maxLength characters"
      InputError.WRONG_FORMAT -> formatMessage
    }
  }

  private fun tooShortMessage(): String =
      if (minLength == maxLength) "Enter all $minLength characters"
      else "Use at least $minLength characters"

  /** Whether the value can be saved as it is. */
  fun isValid(text: String): Boolean = check(text) == null

  private fun hasOnlyAllowedCharacters(value: String): Boolean =
      value.none(::isInvisible) && (allowedCharacters == null || allowedCharacters.matches(value))

  /**
   * Whether [char] is a control character (other than an allowed line break) or unsafe formatting.
   */
  private fun isInvisible(char: Char): Boolean =
      (char.isISOControl() && !(multiLine && char == '\n')) || char.isUnsafeFormatting()
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
  const val DESCRIPTION_MAX_LENGTH = 750
  const val PLACE_QUERY_MAX_LENGTH = 100
  // The longest address SMTP allows (RFC 5321)
  const val EMAIL_MAX_LENGTH = 254

  // The codes FirebaseInvitationCreationRepository generates: no I, O, 0 or 1, which are easy to
  // mix up when read aloud or copied by hand
  const val INVITATION_CODE_LENGTH = 8
  const val INVITATION_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

  /**
   * A person's first name, surname or nickname: letters in any alphabet, with their accents
   * (\p{M}), as words joined by spaces, hyphens or apostrophes (straight, or the curly one many
   * keyboards type): "Anne-Marie", "D'Angelo", "Jean Paul". No digits, emojis or other symbols.
   */
  val NAME =
      InputRule(
          maxLength = NAME_MAX_LENGTH,
          allowedCharacters = Regex("[\\p{L}\\p{M} '’-]*"),
          format = Regex("[\\p{L}\\p{M}]+(?:[ '’-]+[\\p{L}\\p{M}]+)*"),
          formatMessage = "Use only letters, spaces, hyphens (-) and apostrophes (')",
      )

  /** A name that may be left blank, like a surname or a nickname in the Add Person dialog. */
  val OPTIONAL_NAME = NAME.optional()

  /** A short title on one line, like a care circle's name: any characters but invisible ones. */
  val SHORT_TEXT = InputRule(maxLength = SHORT_TEXT_MAX_LENGTH)

  /** A media description: any characters but invisible ones, on several lines. */
  val DESCRIPTION = InputRule(maxLength = DESCRIPTION_MAX_LENGTH, multiLine = true)

  /** A place typed to be searched for. */
  val PLACE_QUERY = InputRule(maxLength = PLACE_QUERY_MAX_LENGTH, required = false)

  /**
   * An email address. Stricter than android.util.Patterns.EMAIL_ADDRESS (which doesn't exist in
   * local unit tests anyway): the part before the @ holds at most 64 characters (RFC 5321) and has
   * no dot at its start, at its end or next to another, and the domain's dots and hyphens each sit
   * between two letters or digits. Spaces are kept as typed, so that "i love@otters.com" is refused
   * rather than silently turned into another address; [InputRule.check] still trims the ends.
   */
  val EMAIL =
      InputRule(
          maxLength = EMAIL_MAX_LENGTH,
          format =
              Regex(
                  "(?=[^@]{1,64}@)[a-zA-Z0-9+_%\\-]+(\\.[a-zA-Z0-9+_%\\-]+)*" +
                      "@" +
                      // Words joined by single dots or hyphens, ending in a dot and a last word
                      "([a-zA-Z0-9]+[.\\-])*[a-zA-Z0-9]+\\.[a-zA-Z0-9]+"
              ),
          formatMessage = "Enter a valid email address",
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
 * Whether this is an invisible formatting character that can hide or reorder text: zero-width
 * spaces, the byte order mark, word joiner, and the marks, overrides and isolates that change the
 * text's direction. The zero-width joiner (U+200D) is kept, since emojis made of several people,
 * like a family, are built with it.
 */
private fun Char.isUnsafeFormatting(): Boolean =
    this == '\u200B' || // Zero-width space
        this == '\u200E' || // Left-to-right mark
        this == '\u200F' || // Right-to-left mark
        this == '\u061C' || // Arabic letter mark
        this == '\u2060' || // Word joiner
        this == '\uFEFF' || // Zero-width no-break space / byte order mark
        this in '\u202A'..'\u202E' || // Bidirectional embeddings and overrides
        this in '\u2066'..'\u2069' // Bidirectional isolates

/**
 * Cuts [text] to [maxLength] characters, without splitting an emoji or other character stored as
 * two (a surrogate pair) in half.
 */
private fun limit(text: String, maxLength: Int): String {
  if (text.length <= maxLength) return text
  val cut = text.take(maxLength)
  return if (cut.last().isHighSurrogate()) cut.dropLast(1) else cut
}
