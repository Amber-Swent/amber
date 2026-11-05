// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by LaMaier, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.e2e

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.amber.AmberApp
import com.github.se.amber.ui.authentication.signIn.EmailSignInTestTags
import com.github.se.amber.ui.authentication.signOut.SignOutButtonTestTags
import com.github.se.amber.ui.caregiver.CaregiverHomeTestTags
import com.github.se.amber.ui.caregiver.DisplayCaregiverButtonTestTags
import com.github.se.amber.ui.caregiver.parameters.CaregiverParametersTestTags
import com.github.se.amber.ui.caregiver.parameters.createInvitation.CREATE_CODE_PLACEHOLDER
import com.github.se.amber.ui.caregiver.parameters.createInvitation.CreateInvitationTestTags
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.CaregiverTab
import com.github.se.amber.ui.onboarding.choice.CaregiverChoiceTestTags
import com.github.se.amber.ui.onboarding.createCareCircle.CreateCareCircleTestTags
import com.github.se.amber.ui.onboarding.createProfile.CreateProfileTestTags
import com.github.se.amber.ui.onboarding.invitationRedemption.InvitationRedemptionTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
import com.google.firebase.Firebase
import com.google.firebase.functions.functions
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
  private val steps = E2eSteps(composeTestRule)

  /**
   * Starts the application against the Firebase emulators and warms up the Functions emulator.
   *
   * The test is skipped when the emulators are unavailable unless they are explicitly required by
   * the instrumentation arguments.
   */
  @Before
  fun startAppOnEmulators() {
    FirebaseEmulator.requireOrSkip()
    FirebaseEmulator.useForDefaultApp()

    // Warms up the Functions emulator (worker cold start + failing Installations lookup) so the
    // journey's first call isn't slow. The call is expected to fail; only the round trip matters.
    // runBlocking makes the test wait for it (safe: @Before is off the main thread), and the
    // timeout stops a stuck emulator from hanging the suite.
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
    steps.click(CaregiverChoiceTestTags.CREATE_BUTTON)
    steps.typeInto(CreateCareCircleTestTags.NAME_FIELD, "Family")
    steps.click(DisplayCaregiverButtonTestTags.BUTTON)
    steps.waitFor(CaregiverHomeTestTags.TITLE)

    val adasCode = createInvitationCode()

    steps.click(BottomNavigationMenuTestTags.tabTag(CaregiverTab.Setting))
    steps.waitFor(CaregiverParametersTestTags.TITLE)
    steps.click(CaregiverParametersTestTags.SIGN_OUT_BUTTON)
    steps.click(SignOutButtonTestTags.BUTTON)
    steps.waitFor(EmailSignInTestTags.GOOGLE_BUTTON)

    signInWithGoogle("bob-$runId@amber.test", "Bob")
    createCaregiverProfile(firstName = "Bob", lastName = "Martin")

    steps.click(CaregiverChoiceTestTags.JOIN_BUTTON)
    steps.typeInto(InvitationRedemptionTestTags.CODE_INPUT, adasCode)

    composeTestRule
        .onNodeWithTag(InvitationRedemptionTestTags.CODE_INPUT)
        .assert(E2eSteps.hasEditableText(adasCode))
    steps.click(InvitationRedemptionTestTags.SUBMIT_BUTTON)
    steps.waitFor(CaregiverHomeTestTags.TITLE)

    val bobsCode = createInvitationCode()
    assertNotEquals(adasCode, bobsCode)
  }

  /**
   * Signs in through the fake Google account picker.
   *
   * @param email the email address to use for the fake account
   * @param name the display name to use for the fake account
   */
  private fun signInWithGoogle(email: String, name: String) {
    googleAccount.switchTo(email, name)
    steps.click(EmailSignInTestTags.GOOGLE_BUTTON)
  }

  /**
   * Fills in a caregiver profile and selects the caregiver role.
   *
   * @param firstName the caregiver's first name
   * @param lastName the caregiver's last name
   */
  private fun createCaregiverProfile(firstName: String, lastName: String) {
    steps.typeInto(CreateProfileTestTags.NAME_INPUT, firstName)
    steps.typeInto(CreateProfileTestTags.SURNAME_INPUT, lastName)
    steps.click(CreateProfileTestTags.CAREGIVER_BUTTON)
  }

  /**
   * Goes from the bottom bar to the invitation screen and creates an invitation code.
   *
   * @return the newly created invitation code
   */
  private fun createInvitationCode(): String {
    steps.click(BottomNavigationMenuTestTags.tabTag(CaregiverTab.Setting))
    steps.click(CaregiverParametersTestTags.CREATE_INVITATION_BUTTON)
    steps.click(CreateInvitationTestTags.CREATE_CARD)
    composeTestRule.waitUntil(E2eSteps.DEFAULT_TIMEOUT_MILLIS) {
      steps.textOf(CreateInvitationTestTags.HINT_BOX)?.let {
        it.isNotBlank() && it != CREATE_CODE_PLACEHOLDER
      } == true
    }
    val code = checkNotNull(steps.textOf(CreateInvitationTestTags.HINT_BOX))
    // the new invitation also reached the list
    steps.waitFor(CreateInvitationTestTags.codeRow(code))
    return code
  }

  private companion object {
    const val WARM_UP_MILLIS = 60_000L
  }
}
