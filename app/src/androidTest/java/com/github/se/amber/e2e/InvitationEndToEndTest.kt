package com.github.se.amber.e2e

/** Written by Viktor Jurczenko, with assistance from
 * Claude (Anthropic) via Claude Code.
 * Modified by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 */

import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToLog
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.se.amber.AmberApp
import com.github.se.amber.ui.accountCreation.CreateProfileTestTags
import com.github.se.amber.ui.accountCreation.caregiverOnboarding.CaregiverChoiceTestTags
import com.github.se.amber.ui.authentication.EmailSignInTestTags
import com.github.se.amber.ui.caregiver.CaregiverHomeTestTags
import com.github.se.amber.ui.caregiver.parameters.CREATE_CODE_PLACEHOLDER
import com.github.se.amber.ui.caregiver.parameters.CaregiverParametersTestTags
import com.github.se.amber.ui.caregiver.parameters.CreateInvitationTestTags
import com.github.se.amber.ui.invitationRedemption.InvitationRedemptionTestTags
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.CaregiverTab
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.onboarding.CreateCareCircleTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
import com.google.firebase.Firebase
import com.google.firebase.functions.functions
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.coroutines.cancellation.CancellationException

/**
 * End-to-end journeys through the real app, its screens and navigation, against the Firebase
 * emulators (see [FirebaseEmulator] for how to start them). Only the Google account picker is
 * faked, by [FakeGoogleAccount].
 *
 * Skipped when the emulators are not running, unless the `requireFirebaseEmulators=true`
 * instrumentation argument (set in CI) makes that a failure. Every user gets a fresh email, so runs
 * don't depend on what earlier runs left in the emulators.
 */
@RunWith(AndroidJUnit4::class)
class InvitationEndToEndTest {

  @get:Rule val composeTestRule = createComposeRule()
  private val googleAccount = FakeGoogleAccount(ApplicationProvider.getApplicationContext())
  private val runId = UUID.randomUUID().toString().take(8)

  @Before
  fun startAppOnEmulators() {
    val message = "Firebase emulators are not running on the host"
    // CI starts the emulators, so there a missing one is broken wiring, not a reason to skip
    val required =
      InstrumentationRegistry.getArguments().getString(REQUIRE_EMULATORS_ARGUMENT) == "true"
    if (required) assertTrue(message, FirebaseEmulator.isRunning())
    else assumeTrue(message, FirebaseEmulator.isRunning())

    FirebaseEmulator.useForDefaultApp()

    // Pays the Functions worker start and the failing Installations lookup here,
    // not in a journey step.
    // The call itself is expected to fail (no auth, bad code); only the warm-up matters.
    runBlocking {
      withTimeoutOrNull(WARM_UP_MILLIS.milliseconds) {
        try {
          Firebase.functions
            .getHttpsCallable("redeemInvitation")
            .call(mapOf("code" to "warm-up"))
            .await()
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          // The warm-up only starts the Functions emulator worker.
        }
      }
    }

    composeTestRule.setContent { AmberAppTheme { AmberApp(credentialManager = googleAccount) } }
  }

  /**
   * Ada signs up, creates a care circle, shares an invitation code from the settings and signs out.
   * Bob signs up on the same phone, joins with Ada's code, and can then invite people to that
   * circle too, which only its members can do.
   */
  @Test
  fun caregiverSharesCode_secondCaregiverJoinsTheCircle() {
    signInWithGoogle("ada-$runId@amber.test", "Ada")
    createCaregiverProfile(firstName = "Ada", lastName = "Lovelace")
    click(CaregiverChoiceTestTags.CREATE_BUTTON.name)
    typeInto(CreateCareCircleTestTags.NAME_FIELD, "Family")
    click(CreateCareCircleTestTags.SUBMIT_BUTTON)
    waitFor(CaregiverHomeTestTags.TITLE)

    val adasCode = createInvitationCode()

    click(BottomNavigationMenuTestTags.tabTag(CaregiverTab.Home))
    waitFor(CaregiverHomeTestTags.TITLE)
    // FIXME: this will fails once the refractor of Home has been done
    click(TopNavigationMenuTestTags.BACK_BUTTON) // signs out
    waitFor(EmailSignInTestTags.GOOGLE_BUTTON)

    signInWithGoogle("bob-$runId@amber.test", "Bob")
    createCaregiverProfile(firstName = "Bob", lastName = "Martin")
    // FIXME : shouldn't use .name. Will be possible once TestTag follow correct UI convention
    //  Fixing this is the responsibility of the one doing the CaregiverChoice Refractor.
    click(CaregiverChoiceTestTags.JOIN_BUTTON.name)
    typeInto(InvitationRedemptionTestTags.CODE_INPUT.name, adasCode)

    // FIXME : shouldn't use .name. Will be possible once TestTag follow correct UI convention
    //  Fixing this is the responsibility of the one doing the InvitationRedemptionScreen Refractor.
    composeTestRule
      .onNodeWithTag(InvitationRedemptionTestTags.CODE_INPUT.name)
      .assert(hasEditableText(adasCode))
    click(InvitationRedemptionTestTags.SUBMIT_BUTTON.name)
    waitFor(CaregiverHomeTestTags.TITLE)

    val bobsCode = createInvitationCode()
    assertNotEquals(adasCode, bobsCode)
  }

  private fun signInWithGoogle(email: String, name: String) {
    googleAccount.switchTo(email, name)
    click(EmailSignInTestTags.GOOGLE_BUTTON)
  }

  /** Fills in the profile; choosing the role saves it. */
  // FIXME : shouldn't use .name. Will be possible once TestTag follow correct UI convention
  //  Fixing this is the responsibility of the one doing the CreateProfile Refractor.
  private fun createCaregiverProfile(firstName: String, lastName: String) {
    typeInto(CreateProfileTestTags.NAME_INPUT.name, firstName)
    typeInto(CreateProfileTestTags.SURNAME_INPUT.name, lastName)
    click(CreateProfileTestTags.CAREGIVER_BUTTON.name)
  }

  /** Goes from the bottom bar to the invitation screen, creates a code and returns it. */
  private fun createInvitationCode(): String {
    click(BottomNavigationMenuTestTags.tabTag(CaregiverTab.Setting))
    click(CaregiverParametersTestTags.CREATE_INVITATION_BUTTON)
    click(CreateInvitationTestTags.CREATE_CARD)
    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      textOf(CreateInvitationTestTags.HINT_BOX)?.let { it.isNotBlank() && it != CREATE_CODE_PLACEHOLDER } == true
    }
    val code = checkNotNull(textOf(CreateInvitationTestTags.HINT_BOX))
    waitFor(CreateInvitationTestTags.codeRow(code)) // the new invitation also reached the list
    return code
  }

  private fun click(tag: String) {
    waitForNode(hasTestTag(tag) and isEnabled(), "enabled node '$tag'")
    composeTestRule.onNodeWithTag(tag).performClick()
  }

  private fun typeInto(tag: String, text: String) {
    waitForNode(hasTestTag(tag) and isEnabled(), "enabled node '$tag'")
    composeTestRule.onNodeWithTag(tag).performTextInput(text)
  }
  private fun textOf(tag: String): String? =
    composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()
      ?.config?.getOrNull(SemanticsProperties.Text)?.joinToString("")

  @OptIn(ExperimentalTestApi::class)
  private fun waitForNode(matcher: SemanticsMatcher, description: String) {
    try {
      composeTestRule.waitUntilExactlyOneExists(matcher, TIMEOUT_MILLIS)
    } catch (e: Exception) {
      composeTestRule.onRoot().printToLog("E2E")
      throw AssertionError("Timed out waiting for $description", e)
    }
  }
  /** Waits for a screen to show [tag]: navigation and backend calls take real time here. */
  private fun waitFor(tag: String) = waitForNode(hasTestTag(tag), "node '$tag'")

  private fun hasEditableText(expected: String) =
    SemanticsMatcher("has editable text '$expected'") {
      it.config.getOrNull(SemanticsProperties.EditableText)?.text == expected
    }

  private companion object {
    const val REQUIRE_EMULATORS_ARGUMENT = "requireFirebaseEmulators"
    // the first callable call starts the Functions emulator's worker, which takes a few seconds
    const val TIMEOUT_MILLIS = 30_000L
    const val WARM_UP_MILLIS = 60_000L
  }
}
