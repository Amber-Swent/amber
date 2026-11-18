// Written by Yuhan YANG, with assistance from Cursor
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by MeiliV, with assistance from OpenAI Codex
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.caregiver.parameters.createIntivation

import com.github.se.amber.R
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.invitation.InvitationCreationRepository
import com.github.se.amber.data.invitation.InvitationCreationResult
import com.github.se.amber.data.invitation.InvitationLookupRepository
import com.github.se.amber.data.invitation.InvitationStatus
import com.github.se.amber.data.invitation.InvitationSummary
import com.github.se.amber.data.profile.ProfileReadError
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.caregiver.parameters.createInvitation.CreateInvitationViewModel
import io.mockk.every
import io.mockk.mockk
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests [CreateInvitationViewModel] with in-memory repositories.
 *
 * Covers the active circle, invitation loading, creation failures, a second click while a creation
 * is still running, and an unexpected creation exception. No test talks to Firebase.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CreateInvitationViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private lateinit var profiles: FakeProfileRepository
  private lateinit var lookup: FakeLookupRepository
  private lateinit var creation: FakeCreationRepository
  private val activeCircleRepository = mockk<ActiveCircleRepository>()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    profiles = FakeProfileRepository(Result.success(profile("circle-1")))
    lookup = FakeLookupRepository()
    creation = FakeCreationRepository()
    every { activeCircleRepository.activeCircleId(any(), any()) } answers
        {
          flowOf(secondArg<List<String>>().firstOrNull { it.isNotBlank() })
        }
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun noCareCircleSetsErrorAndDoesNotLoadInvitations() =
      runTest(dispatcher) {
        profiles.result = Result.success(profile())

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(
            R.string.createInvitationErrorNoCircle,
            viewModel.uiState.value.errorMsgRes,
        )
        assertNull(viewModel.uiState.value.selectedCircleId)
        assertEquals(emptyList<String>(), lookup.requestedCircleIds)
        assertEquals(emptyList<String>(), creation.requestedCircleIds)
      }

  @Test
  fun blankCircleIdsDoNotSelectOrLoadACircle() =
      runTest(dispatcher) {
        profiles.result = Result.success(profile("", "  "))

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(R.string.createInvitationErrorNoCircle, viewModel.uiState.value.errorMsgRes)
        assertNull(viewModel.uiState.value.selectedCircleId)
        assertEquals(emptyList<String>(), lookup.requestedCircleIds)
        assertEquals(emptyList<String>(), creation.requestedCircleIds)
      }

  @Test
  fun blankCircleIdsAreFilteredWhenAUsableCircleExists() =
      runTest(dispatcher) {
        profiles.result = Result.success(profile(" ", "circle-1", ""))

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals("circle-1", viewModel.uiState.value.selectedCircleId)
        assertEquals(listOf("circle-1"), lookup.requestedCircleIds)
      }

  @Test
  fun nullActiveCircleShowsErrorDespiteUsableCircleIds() =
      runTest(dispatcher) {
        every { activeCircleRepository.activeCircleId(any(), any()) } returns flowOf(null)

        val viewModel = viewModel()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedCircleId)
        assertEquals(R.string.createInvitationErrorNoCircle, viewModel.uiState.value.errorMsgRes)
        assertTrue(lookup.requestedCircleIds.isEmpty())
      }

  @Test
  fun createAfterProfileRecoversButActiveCircleIsNullShowsError() =
      runTest(dispatcher) {
        profiles.result = Result.failure(ProfileReadError.Network)
        val viewModel = viewModel()
        advanceUntilIdle()
        profiles.result = Result.success(profile("circle-1"))
        every { activeCircleRepository.activeCircleId(any(), any()) } returns flowOf(null)

        viewModel.createInvitation()
        advanceUntilIdle()

        assertEquals(R.string.createInvitationErrorNoCircle, viewModel.uiState.value.errorMsgRes)
        assertNull(viewModel.uiState.value.selectedCircleId)
        assertFalse(viewModel.uiState.value.isCreating)
        assertTrue(creation.requestedCircleIds.isEmpty())
      }

  @Test
  fun missingProfileSetsNotFound() =
      runTest(dispatcher) {
        profiles.result = Result.success(null)

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(
            R.string.createInvitationErrorProfileNotFound,
            viewModel.uiState.value.errorMsgRes,
        )
        assertNull(viewModel.uiState.value.selectedCircleId)
      }

  @Test
  fun profileReadErrorShowsAFixedMessage() =
      runTest(dispatcher) {
        profiles.result = Result.failure(ProfileReadError.Network)

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(
            R.string.createInvitationErrorLoadProfile,
            viewModel.uiState.value.errorMsgRes,
        )
        assertEquals(emptyList<String>(), lookup.requestedCircleIds)
      }

  @Test
  fun blankProfileErrorUsesFallback() =
      runTest(dispatcher) {
        profiles.result = Result.failure(IllegalStateException(" "))

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(
            R.string.createInvitationErrorLoadProfile,
            viewModel.uiState.value.errorMsgRes,
        )
      }

  @Test
  fun loadsTheActiveCircleInvitations() =
      runTest(dispatcher) {
        val stored = invitation("OLD12345")
        lookup.results = mapOf("circle-1" to Result.success(listOf(stored)))

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals("circle-1", viewModel.uiState.value.selectedCircleId)
        assertEquals(listOf(stored), viewModel.uiState.value.invitations)
        assertNull(viewModel.uiState.value.errorMsgRes)
        assertNull(viewModel.uiState.value.generatedCode)
      }

  @Test
  fun startsWithTheActiveCircleWhenItIsSecond() =
      runTest(dispatcher) {
        profiles.result = Result.success(profile("circle-1", "circle-2"))
        every {
          activeCircleRepository.activeCircleId("caregiver-1", listOf("circle-1", "circle-2"))
        } returns flowOf("circle-2")

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals("circle-2", viewModel.uiState.value.selectedCircleId)
        assertEquals(listOf("circle-2"), lookup.requestedCircleIds)
      }

  @Test
  fun createStoresCodeAndRefreshedInvitations() =
      runTest(dispatcher) {
        lookup.results = mapOf("circle-1" to Result.success(listOf(invitation("OLD12345"))))
        val viewModel = viewModel()
        advanceUntilIdle()
        val created = invitation("AB12CD34")
        lookup.results = mapOf("circle-1" to Result.success(listOf(created)))
        creation.result = InvitationCreationResult.Created("AB12CD34")

        viewModel.createInvitation()
        advanceUntilIdle()

        assertEquals("AB12CD34", viewModel.uiState.value.generatedCode)
        assertEquals(listOf(created), viewModel.uiState.value.invitations)
        assertNull(viewModel.uiState.value.errorMsgRes)
        assertEquals(listOf("circle-1"), creation.requestedCircleIds)
        assertTrue(!viewModel.uiState.value.isCreating)
      }

  @Test
  fun creationFailuresUseTheirOwnMessagesAndKeepTheList() =
      runTest(dispatcher) {
        val existing = invitation("OLD12345")
        lookup.results = mapOf("circle-1" to Result.success(listOf(existing)))
        val viewModel = viewModel()
        advanceUntilIdle()
        val cases =
            listOf(
                InvitationCreationResult.NotAuthenticated to
                    R.string.createInvitationErrorNotAuthenticated,
                InvitationCreationResult.PermissionDenied to
                    R.string.createInvitationErrorPermissionDenied,
                InvitationCreationResult.NetworkFailure to R.string.createInvitationErrorNetwork,
                InvitationCreationResult.InvalidExpiry to
                    R.string.createInvitationErrorInvalidExpiry,
                InvitationCreationResult.CollisionRetryLimitExceeded to
                    R.string.createInvitationErrorCodesTaken,
                InvitationCreationResult.FirestoreFailure(IllegalStateException(" ")) to
                    R.string.createInvitationErrorSave,
                InvitationCreationResult.FirestoreFailure(
                    IllegalStateException("quota exceeded")
                ) to R.string.createInvitationErrorSave,
            )

        cases.forEach { (result, messageRes) ->
          creation.result = result
          viewModel.createInvitation()
          advanceUntilIdle()

          assertEquals(messageRes, viewModel.uiState.value.errorMsgRes)
          assertNull(viewModel.uiState.value.generatedCode)
          assertEquals(listOf(existing), viewModel.uiState.value.invitations)
        }
        assertEquals(List(cases.size) { "circle-1" }, creation.requestedCircleIds)
      }

  @Test
  fun lookupFailureShowsAFixedMessage() =
      runTest(dispatcher) {
        lookup.results =
            mapOf(
                "circle-1" to
                    Result.failure(IllegalStateException("Invitation bad has an invalid format")),
            )
        val withMessage = viewModel()
        advanceUntilIdle()
        assertEquals(
            R.string.createInvitationErrorLoadInvitations,
            withMessage.uiState.value.errorMsgRes,
        )

        lookup.results = mapOf("circle-1" to Result.failure(IllegalStateException(" ")))
        val withoutMessage = viewModel()
        advanceUntilIdle()
        assertEquals(
            R.string.createInvitationErrorLoadInvitations,
            withoutMessage.uiState.value.errorMsgRes,
        )
      }

  @Test
  fun listRefreshFailureAfterCreateKeepsTheCode() =
      runTest(dispatcher) {
        val existing = invitation("OLD12345")
        lookup.results = mapOf("circle-1" to Result.success(listOf(existing)))
        val viewModel = viewModel()
        advanceUntilIdle()
        lookup.results = mapOf("circle-1" to Result.failure(IllegalStateException(" ")))
        creation.result = InvitationCreationResult.Created("AB12CD34")

        viewModel.createInvitation()
        advanceUntilIdle()

        assertEquals("AB12CD34", viewModel.uiState.value.generatedCode)
        assertEquals(listOf(existing), viewModel.uiState.value.invitations)
        assertEquals(
            R.string.createInvitationErrorRefreshAfterCreate,
            viewModel.uiState.value.errorMsgRes,
        )
      }

  @Test
  fun secondCreateWhileRunningIsIgnored() =
      runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        creation.pause = true

        viewModel.createInvitation()
        runCurrent()
        viewModel.createInvitation()
        runCurrent()

        assertEquals(listOf("circle-1"), creation.requestedCircleIds)
        assertEquals("circle-1", viewModel.uiState.value.selectedCircleId)
        assertTrue(viewModel.uiState.value.isCreating)

        creation.release.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("circle-1"), creation.requestedCircleIds)
        assertEquals("AB12CD34", viewModel.uiState.value.generatedCode)
        assertTrue(!viewModel.uiState.value.isCreating)
      }

  @Test
  fun cancelledCreationClearsCreatingWithoutShowingAnError() =
      runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        creation.thrown = CancellationException("cancelled")

        viewModel.createInvitation()
        advanceUntilIdle()

        assertEquals(listOf("circle-1"), creation.requestedCircleIds)
        assertTrue(!viewModel.uiState.value.isCreating)
        assertNull(viewModel.uiState.value.errorMsgRes)
      }

  @Test
  fun createInvitationThrowSetsErrorAndClearsCreating() =
      runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        creation.thrown = IllegalStateException("firestore exploded")

        viewModel.createInvitation()
        advanceUntilIdle()

        assertEquals(R.string.createInvitationErrorCreate, viewModel.uiState.value.errorMsgRes)
        assertTrue(!viewModel.uiState.value.isCreating)
      }

  @Test
  fun createWithoutACircleDoesNotCallTheServer() =
      runTest(dispatcher) {
        profiles.result = Result.success(profile())
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.createInvitation()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedCircleId)
        assertEquals(R.string.createInvitationErrorNoCircle, viewModel.uiState.value.errorMsgRes)
        assertEquals(emptyList<String>(), creation.requestedCircleIds)
        assertTrue(!viewModel.uiState.value.isCreating)
      }

  @Test
  fun createLoadsACircleThatAppearsAfterTheScreenOpened() =
      runTest(dispatcher) {
        profiles.result = Result.success(profile())
        val viewModel = viewModel()
        advanceUntilIdle()
        profiles.result = Result.success(profile("circle-1"))
        lookup.results = mapOf("circle-1" to Result.success(emptyList()))
        creation.result = InvitationCreationResult.Created("AB12CD34")

        viewModel.createInvitation()
        advanceUntilIdle()

        assertEquals("circle-1", viewModel.uiState.value.selectedCircleId)
        assertEquals("AB12CD34", viewModel.uiState.value.generatedCode)
        assertEquals(listOf("circle-1"), creation.requestedCircleIds)
        assertEquals(listOf("circle-1"), lookup.requestedCircleIds)
        assertNull(viewModel.uiState.value.errorMsgRes)
      }

  @Test
  fun copyCodeExposesTheCodeAndTheMessageUntilCleared() =
      runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.copyCode("SECOND12")

        assertEquals("SECOND12", viewModel.uiState.value.codeToCopy)
        assertEquals(
            R.string.createInvitationCodeCopied,
            viewModel.uiState.value.copiedMessageRes,
        )

        viewModel.clearCopiedCode()

        assertNull(viewModel.uiState.value.codeToCopy)
        assertNull(viewModel.uiState.value.copiedMessageRes)
      }

  private fun viewModel() =
      CreateInvitationViewModel(
          profileRepository = profiles,
          creationRepository = creation,
          lookupRepository = lookup,
          activeCircleRepository = activeCircleRepository,
      )

  private fun profile(vararg circleIds: String) =
      UserProfile(
          uid = "caregiver-1",
          role = Role.CAREGIVER,
          person = Person(id = "caregiver-1", firstName = "Ada"),
          circleIds = circleIds.toList(),
      )

  private fun invitation(code: String) =
      InvitationSummary(
          code = code,
          createdAt = 1_700_000_000_000L,
          expiresAt = 1_700_000_000_000L + 86_400_000L,
          status = InvitationStatus.ACTIVE,
      )
}

private class FakeProfileRepository(var result: Result<UserProfile?>) : ProfileRepository {
  override suspend fun createProfile(person: Person, role: Role): Result<Unit> = error("not used")

  override suspend fun updateOnboardingProfile(person: Person, role: Role): Result<Unit> =
      error("not used")

  override suspend fun getCurrentProfile(): Result<UserProfile?> = result

  override suspend fun getProfile(uid: String): Result<UserProfile?> = result
}

private class FakeLookupRepository : InvitationLookupRepository {
  val requestedCircleIds = mutableListOf<String>()
  var results: Map<String, Result<List<InvitationSummary>>> = emptyMap()
  val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
  val ignoreCancellationFor = mutableSetOf<String>()

  override suspend fun getInvitations(selectedCircleId: String): Result<List<InvitationSummary>> {
    requestedCircleIds += selectedCircleId
    if (selectedCircleId in ignoreCancellationFor) {
      withContext(NonCancellable) { gates[selectedCircleId]?.await() }
    } else {
      gates[selectedCircleId]?.await()
    }
    return results[selectedCircleId] ?: Result.success(emptyList())
  }
}

private class FakeCreationRepository : InvitationCreationRepository {
  val requestedCircleIds = mutableListOf<String>()
  var result: InvitationCreationResult = InvitationCreationResult.Created("AB12CD34")
  var pause = false
  var thrown: Exception? = null
  val release = CompletableDeferred<Unit>()

  override suspend fun createInvitation(selectedCircleId: String): InvitationCreationResult {
    requestedCircleIds += selectedCircleId
    if (pause) release.await()
    thrown?.let { throw it }
    return result
  }
}
