// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.ui.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
import com.github.se.amber.model.validation.InputRule
import com.github.se.amber.model.validation.InputValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tests how [errorText] turns a field's validation error into a message in the app language. */
@RunWith(RobolectricTestRunner::class)
class InputValidationErrorTextTest {

  private val code = InputValidation.INVITATION_CODE

  @Test
  fun validValue_showsNothing() {
    assertNull(InputValidation.NAME.errorText("Anne-Marie"))
  }

  @Test
  fun blankValue_showsTheBlankMessageOnlyOnceSubmitted() {
    assertNull(code.errorText(" ", submitted = false, blankRes = R.string.inputErrorBlank))
    assertEquals(
        UiText.Resource(R.string.invitationRedemptionCodeRequired),
        code.errorText(" ", blankRes = R.string.invitationRedemptionCodeRequired),
    )
    assertNull(code.errorText(" ", blankRes = null))
  }

  @Test
  fun tooLongValue_showsTheRuleLimitEvenWhileTyping() {
    assertEquals(
        UiText.Plural(R.plurals.inputErrorTooLong, 3),
        InputRule(maxLength = 3).errorText("abcd", submitted = false),
    )
  }

  @Test
  fun tooShortValue_asksForAllCharactersWhenTheLengthIsFixed() {
    assertNull(code.errorText("ABCD", submitted = false))
    assertEquals(
        UiText.Plural(R.plurals.inputErrorIncomplete, InputValidation.INVITATION_CODE_LENGTH),
        code.errorText("ABCD"),
    )
    assertEquals(
        UiText.Plural(R.plurals.inputErrorTooShort, 2),
        InputRule(maxLength = 3, minLength = 2).errorText("a"),
    )
  }

  @Test
  fun wrongFormat_showsTheFieldFormatMessage() {
    assertEquals(
        UiText.Resource(R.string.inputErrorInvitationCodeFormat),
        code.errorText(
            "AB0",
            submitted = false,
            formatRes = R.string.inputErrorInvitationCodeFormat,
        ),
    )
    assertEquals(UiText.Resource(R.string.inputErrorFormat), InputValidation.NAME.errorText("Ada!"))
    // An unfinished name waits for submission
    assertNull(InputValidation.NAME.errorText("Anne-", submitted = false))
  }

  @Test
  fun blankValueWithoutBlankMessage_showsNothingWhileTyping() {
    assertNull(code.errorText(" ", submitted = false, blankRes = null))
  }

  @Test
  fun tooLongValueWithWrongCharacters_showsTheLengthFirst() {
    assertEquals(
        UiText.Plural(R.plurals.inputErrorTooLong, 3),
        InputRule(maxLength = 3, allowedCharacters = Regex("[a-z]*")).errorText("ab1d", false),
    )
  }

  @Test
  @Config(qualifiers = "fr")
  fun everyMessageIsTranslatedAndKeepsItsLimit() {
    val resources = ApplicationProvider.getApplicationContext<Context>().resources
    val expected =
        mapOf(
            InputRule(maxLength = 3).errorText("abcd") to "Utilisez au plus 3 caractères",
            InputRule(maxLength = 1).errorText("ab") to "Utilisez au plus 1 caractère",
            code.errorText("ABCD") to "Saisissez les 8 caractères",
            InputRule(maxLength = 3, minLength = 2).errorText("a") to
                "Utilisez au moins 2 caractères",
            code.errorText(" ") to "Veuillez remplir ce champ",
            InputValidation.NAME.errorText("Ada!") to
                "Ce champ contient des caractères non autorisés",
            InputValidation.NAME.errorText("Ada!", formatRes = R.string.inputErrorNameFormat) to
                "Utilisez uniquement des lettres, des espaces, des traits d'union (-) et des " +
                    "apostrophes (')",
            InputValidation.EMAIL.errorText("a@", formatRes = R.string.inputErrorEmailFormat) to
                "Saisissez une adresse e-mail valide",
        )

    expected.forEach { (text, french) -> assertEquals(french, text?.asString(resources)) }
  }

  @Test
  fun lengthMessagesUseTheSingularForOneCharacter() {
    val resources = ApplicationProvider.getApplicationContext<Context>().resources

    assertEquals(
        "Use at most 1 character",
        InputRule(maxLength = 1).errorText("ab")?.asString(resources),
    )
    assertEquals(
        "Use at most 3 characters",
        InputRule(maxLength = 3).errorText("abcd")?.asString(resources),
    )
  }
}
