// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
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
        UiText.Resource(R.string.inputErrorTooLong, listOf(3)),
        InputRule(maxLength = 3).errorText("abcd", submitted = false),
    )
  }

  @Test
  fun tooShortValue_asksForAllCharactersWhenTheLengthIsFixed() {
    assertNull(code.errorText("ABCD", submitted = false))
    assertEquals(
        UiText.Resource(
            R.string.inputErrorIncomplete,
            listOf(InputValidation.INVITATION_CODE_LENGTH),
        ),
        code.errorText("ABCD"),
    )
    assertEquals(
        UiText.Resource(R.string.inputErrorTooShort, listOf(2)),
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
  @Config(qualifiers = "fr")
  fun messagesAreTranslatedAndKeepTheLimit() {
    val resources = ApplicationProvider.getApplicationContext<Context>().resources

    assertEquals(
        "Utilisez au plus 3 caractères",
        InputRule(maxLength = 3).errorText("abcd")?.asString(resources),
    )
  }
}
