// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Tests the rules of [InputValidation] and how an [InputRule] cleans and checks text. */
class InputValidationTest {

  @Test
  fun name_acceptsNamesInAnyAlphabetWithSpacesHyphensAndApostrophes() {
    listOf("Anne-Marie", "D'Angelo", "D’Angelo", "Jean Paul", "Zoë", "नेहा", "  Ada  ").forEach {
      assertNull(it, InputValidation.NAME.check(it))
    }
  }

  @Test
  fun name_refusesDigitsSymbolsEmojisAndLoosePunctuation() {
    listOf("Ada2", "Ada!", "Ada 😀", "-Ada", "Ada-", "' '").forEach {
      assertEquals(it, InputError.WRONG_FORMAT, InputValidation.NAME.check(it))
    }
  }

  @Test
  fun name_blankIsRefusedUnlessOptional() {
    assertEquals(InputError.BLANK, InputValidation.NAME.check(" \t "))
    assertNull(InputValidation.NAME.optional().check(" \t "))
    assertEquals(InputError.WRONG_FORMAT, InputValidation.NAME.optional().check("Ada2"))
  }

  @Test
  fun clean_stopsAtMaxLengthWithoutSplittingAnEmoji() {
    val rule = InputRule(maxLength = 3)

    assertEquals("abc", rule.clean("abcdef"))
    // 😀 takes two chars: keeping only its first half would leave a broken character
    assertEquals("ab", rule.clean("ab😀"))
    assertEquals(
        InputValidation.NAME_MAX_LENGTH,
        InputValidation.NAME.clean("a".repeat(500)).length,
    )
  }

  @Test
  fun check_refusesTooLongTextThatWasNotCleaned() {
    assertEquals(
        InputError.TOO_LONG,
        InputValidation.SHORT_TEXT.check("a".repeat(InputValidation.SHORT_TEXT_MAX_LENGTH + 1)),
    )
  }

  @Test
  fun freeText_removesControlCharactersButADescriptionKeepsLineBreaks() {
    assertEquals("Familyname", InputValidation.SHORT_TEXT.clean("Family\nname\u0000"))
    assertEquals("Line 1\nLine 2", InputValidation.DESCRIPTION.clean("Line 1\r\nLine 2\u001B"))
    assertNull(InputValidation.DESCRIPTION.check("Beach day 🏖️\nwith Nana"))
    assertEquals(InputError.WRONG_FORMAT, InputValidation.SHORT_TEXT.check("Family\u0007"))
  }

  @Test
  fun email_acceptsAddressesAndRefusesMalformedOnes() {
    assertNull(InputValidation.EMAIL.check("person.name+tag@mail.example.ch"))
    listOf("person", "person@", "@example.com", "person@example", "per son@example.com").forEach {
      assertEquals(it, InputError.WRONG_FORMAT, InputValidation.EMAIL.check(it))
    }
    assertEquals("person@example.com", InputValidation.EMAIL.clean(" person @example.com "))
  }

  @Test
  fun invitationCode_isCleanedLikeGeneratedCodesAndNeedsTheirExactFormat() {
    val code = InputValidation.INVITATION_CODE

    assertEquals("ABCD2345", code.clean(" abcd 2345 6789"))
    assertNull(code.check("ABCD2345"))
    assertEquals(InputError.TOO_SHORT, code.check("ABCD"))
    // I, O, 0 and 1 are never generated
    assertEquals(InputError.WRONG_FORMAT, code.check("ABCD2340"))
    assertEquals(InputError.WRONG_FORMAT, code.check("AB-D2345"))
  }

  @Test
  fun errorMessage_whileTypingOnlyShowsWhatTypingMoreCannotFix() {
    val code = InputValidation.INVITATION_CODE

    assertNull(code.errorMessage("", submitted = false, blankMessage = "Enter a code"))
    assertNull(code.errorMessage("ABCD", submitted = false))
    assertEquals(code.formatMessage, code.errorMessage("AB0", submitted = false))
    // An unfinished name, like "Anne-" on the way to "Anne-Marie", waits for submission
    assertNull(InputValidation.NAME.errorMessage("Anne-", submitted = false))
  }

  @Test
  fun errorMessage_onceSubmittedShowsEveryError() {
    val code = InputValidation.INVITATION_CODE

    assertEquals("Enter a code", code.errorMessage(" ", blankMessage = "Enter a code"))
    assertEquals("Use at least 8 characters", code.errorMessage("ABCD"))
    assertEquals(InputValidation.NAME.formatMessage, InputValidation.NAME.errorMessage("Anne-"))
    assertEquals("Use at most 3 characters", InputRule(maxLength = 3).errorMessage("abcd"))
    assertNull(InputValidation.NAME.errorMessage("Anne-Marie"))
    assertNull(InputValidation.NAME.errorMessage("", blankMessage = null))
  }

  @Test
  fun rule_refusesImpossibleLengths() {
    assertThrows(IllegalArgumentException::class.java) { InputRule(maxLength = 0) }
    assertThrows(IllegalArgumentException::class.java) { InputRule(maxLength = 3, minLength = 4) }
  }
}
