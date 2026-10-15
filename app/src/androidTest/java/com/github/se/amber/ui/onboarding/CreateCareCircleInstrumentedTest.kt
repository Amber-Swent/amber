// This file was written with the aid of AI.
package com.github.se.amber.ui.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.amber.data.onboarding.OnboardingError
import com.github.se.amber.data.onboarding.OnboardingRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.ui.theme.AmberAppTheme
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real Compose screen and ViewModel with a local repository on Android. */
@RunWith(AndroidJUnit4::class)
class CreateCareCircleInstrumentedTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private lateinit var repository: FakeOnboardingRepository
  private lateinit var viewModel: CreateCareCircleViewModel
  private val createdCount = AtomicInteger(0)

  @Before
  fun setUp() {
    repository = FakeOnboardingRepository()
    viewModel = CreateCareCircleViewModel(repository)
    createdCount.set(0)
    composeTestRule.setContent {
      AmberAppTheme(darkTheme = false, dynamicColor = false) {
        CreateCareCircleScreen(
            onCreated = { createdCount.incrementAndGet() },
            viewModel = viewModel,
        )
      }
    }
  }

  @Test
  fun blankSubmissionShowsSupportingError() {
    submitBlankName()
    composeTestRule
        .onNodeWithTag(CreateCareCircleTestTags.NAME_ERROR, useUnmergedTree = true)
        .assertIsDisplayed()
  }

  @Test
  fun loadingDisablesCreateButton() {
    submitPendingName()
    composeTestRule.onNodeWithTag(CreateCareCircleTestTags.SUBMIT_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun successEmitsOneCallback() {
    val gate = submitPendingName()
    gate.complete(Unit)
    composeTestRule.waitUntil { viewModel.uiState.value.isCreated }
    composeTestRule.waitForIdle()
    assertEquals(1, createdCount.get())
  }

  @Test
  fun failureAllowsRetryAndThenEmitsSuccess() {
    submitWithNetworkFailure()
    composeTestRule.onNodeWithTag(CreateCareCircleTestTags.SUBMIT_BUTTON).performClick()
    composeTestRule.waitUntil { viewModel.uiState.value.isCreated }
    composeTestRule.waitForIdle()
    assertEquals(1, createdCount.get())
  }

  private fun submitBlankName() {
    composeTestRule.onNodeWithTag(CreateCareCircleTestTags.SUBMIT_BUTTON).performClick()
  }

  private fun submitName(name: String = "Family") {
    composeTestRule.onNodeWithTag(CreateCareCircleTestTags.NAME_FIELD).performTextInput(name)
    composeTestRule.onNodeWithTag(CreateCareCircleTestTags.SUBMIT_BUTTON).performClick()
  }

  private fun submitPendingName(): CompletableDeferred<Unit> {
    val gate = CompletableDeferred<Unit>()
    repository.gate = gate
    submitName()
    composeTestRule.waitUntil { repository.submittedNames.size == 1 }
    return gate
  }

  private fun submitWithNetworkFailure() {
    repository.results.add(Result.failure(OnboardingError.Network))
    submitName()
    composeTestRule.waitUntil {

      /*
        Checks that the first request completed
        Relying only on repository.submittedNames.size == 1 would check that the request is being
        sent, not that it indeed failed
      */
      repository.completedRequests.get() == 1 &&
              !viewModel.uiState.value.isLoading
    }
  }

  private class FakeOnboardingRepository : OnboardingRepository {
    val submittedNames = ConcurrentLinkedQueue<String>()
    val results = ConcurrentLinkedQueue<Result<CareCircle>>()
    val completedRequests = AtomicInteger(0)
    @Volatile var gate: CompletableDeferred<Unit>? = null

    override suspend fun createInitialCareCircle(name: String): Result<CareCircle> {
      submittedNames.add(name)
      gate?.await()
      val result =
        results.poll()
          ?: Result.success(CareCircle(id = "circle-1", name = name))

      completedRequests.incrementAndGet()
      return result
    }
  }
}
