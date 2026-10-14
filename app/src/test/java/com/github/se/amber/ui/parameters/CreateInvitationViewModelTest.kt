/* Written by Yuhan YANG, with assistance from
 * Cursor.
 */
package com.github.se.amber.ui.parameters

import com.github.se.amber.data.circle.CareCircleError
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.invitation.InvitationCreationRepository
import com.github.se.amber.data.invitation.InvitationCreationResult
import com.github.se.amber.data.invitation.InvitationLookupRepository
import com.github.se.amber.data.invitation.InvitationStatus
import com.github.se.amber.data.invitation.InvitationSummary
import com.github.se.amber.data.profile.ProfileReadError
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests [CreateInvitationViewModel] with in-memory repositories.
 *
 * Covers circle selection, invitation loading, creation failures, and a second click while a
 * creation is still running. No test talks to Firebase.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CreateInvitationViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private lateinit var profiles: FakeProfileRepository
  private lateinit var circles: FakeCircleRepository
  private lateinit var lookup: FakeLookupRepository
  private lateinit var creation: FakeCreationRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    profiles = FakeProfileRepository(Result.success(profile("circle-1")))
    circles = FakeCircleRepository(mapOf("circle-1" to circle("circle-1", "Arthur's family")))
    lookup = FakeLookupRepository()
    creation = FakeCreationRepository()
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

        assertEquals("No care circle", viewModel.uiState.value.errorMessage)
        assertEquals(emptyList<CareCircleChoice>(), viewModel.uiState.value.circles)
        assertEquals(emptyList<String>(), lookup.requestedCircleIds)
        assertEquals(emptyList<String>(), creation.requestedCircleIds)
      }

  @Test
  fun missingProfileSetsNotFound() =
      runTest(dispatcher) {
        profiles.result = Result.success(null)

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals("Your profile was not found", viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.selectedCircleId)
      }

  @Test
  fun profileReadErrorUsesItsMessage() =
      runTest(dispatcher) {
        profiles.result = Result.failure(ProfileReadError.Network)

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(ProfileReadError.Network.message, viewModel.uiState.value.errorMessage)
        assertEquals(emptyList<String>(), lookup.requestedCircleIds)
      }

  @Test
  fun blankProfileErrorUsesFallback() =
      runTest(dispatcher) {
        profiles.result = Result.failure(IllegalStateException(" "))

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals("Could not load your profile", viewModel.uiState.value.errorMessage)
      }

  @Test
  fun loadsCircleNameAndInvitations() =
      runTest(dispatcher) {
        val stored = invitation("OLD12345")
        lookup.results = mapOf("circle-1" to Result.success(listOf(stored)))

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(
            listOf(CareCircleChoice("circle-1", "Arthur's family")),
            viewModel.uiState.value.circles,
        )
        assertEquals("circle-1", viewModel.uiState.value.selectedCircleId)
        assertEquals(listOf(stored), viewModel.uiState.value.invitations)
        assertNull(viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.generatedCode)
      }

  @Test
  fun unavailableOrBlankCircleNameUsesTheCircleId() =
      runTest(dispatcher) {
        circles = FakeCircleRepository(mapOf("circle-1" to circle("circle-1", "  ")))
        val blankName = viewModel()
        advanceUntilIdle()
        assertEquals(
            listOf(CareCircleChoice("circle-1", "circle-1")),
            blankName.uiState.value.circles,
        )

        circles = FakeCircleRepository(emptyMap())
        val missingCircle = viewModel()
        advanceUntilIdle()
        assertEquals(
            listOf(CareCircleChoice("circle-1", "circle-1")),
            missingCircle.uiState.value.circles,
        )
      }

  @Test
  fun severalCirclesStartOnTheFirstUntilTheUserSwitches() =
      runTest(dispatcher) {
        profiles.result = Result.success(profile("circle-1", "circle-2"))
        circles =
            FakeCircleRepository(
                mapOf(
                    "circle-1" to circle("circle-1", "Arthur's family"),
                    "circle-2" to circle("circle-2", "Bea's family"),
                ),
            )
        val first = invitation("FIRST111")
        val second = invitation("SECOND22")
        lookup.results =
            mapOf(
                "circle-1" to Result.success(listOf(first)),
                "circle-2" to Result.success(listOf(second)),
            )
        val viewModel = viewModel()
        advanceUntilIdle()
        creation.result = InvitationCreationResult.Created("AB12CD34")
        viewModel.createInvitation()
        advanceUntilIdle()

        viewModel.selectCircle("circle-2")
        advanceUntilIdle()

        assertEquals(
            listOf(
                CareCircleChoice("circle-1", "Arthur's family"),
                CareCircleChoice("circle-2", "Bea's family"),
            ),
            viewModel.uiState.value.circles,
        )
        assertEquals("circle-2", viewModel.uiState.value.selectedCircleId)
        assertEquals(listOf(second), viewModel.uiState.value.invitations)
        assertNull(viewModel.uiState.value.generatedCode)
        assertEquals(listOf("circle-1", "circle-1", "circle-2"), lookup.requestedCircleIds)
      }

  @Test
  fun unknownCircleSelectionIsIgnored() =
      runTest(dispatcher) {
        lookup.results = mapOf("circle-1" to Result.success(listOf(invitation("OLD12345"))))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.selectCircle("missing")
        advanceUntilIdle()

        assertEquals("circle-1", viewModel.uiState.value.selectedCircleId)
        assertEquals(listOf("circle-1"), lookup.requestedCircleIds)
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
        assertNull(viewModel.uiState.value.errorMessage)
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
                InvitationCreationResult.NotAuthenticated to "Sign in to create an invitation",
                InvitationCreationResult.PermissionDenied to
                    "You do not have permission to create an invitation",
                InvitationCreationResult.NetworkFailure to
                    "Network error. Check your connection and try again",
                InvitationCreationResult.InvalidExpiry to
                    "The invitation expiration date is invalid",
                InvitationCreationResult.CollisionRetryLimitExceeded to
                    "Every generated invitation code was already taken. Try again",
                InvitationCreationResult.FirestoreFailure(IllegalStateException(" ")) to
                    "Saving the invitation failed",
                InvitationCreationResult.FirestoreFailure(
                    IllegalStateException("quota exceeded")
                ) to "Saving the invitation failed: quota exceeded",
            )

        cases.forEach { (result, message) ->
          creation.result = result
          viewModel.createInvitation()
          advanceUntilIdle()

          assertEquals(message, viewModel.uiState.value.errorMessage)
          assertNull(viewModel.uiState.value.generatedCode)
          assertEquals(listOf(existing), viewModel.uiState.value.invitations)
        }
        assertEquals(List(cases.size) { "circle-1" }, creation.requestedCircleIds)
      }

  @Test
  fun lookupFailureUsesTheThrowableMessageOrAFallback() =
      runTest(dispatcher) {
        lookup.results =
            mapOf(
                "circle-1" to
                    Result.failure(IllegalStateException("Invitation bad has an invalid format")),
            )
        val withMessage = viewModel()
        advanceUntilIdle()
        assertEquals(
            "Invitation bad has an invalid format",
            withMessage.uiState.value.errorMessage,
        )

        lookup.results = mapOf("circle-1" to Result.failure(IllegalStateException(" ")))
        val withoutMessage = viewModel()
        advanceUntilIdle()
        assertEquals("Could not load invitations", withoutMessage.uiState.value.errorMessage)
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
            "The invitation was created, but the list could not be refreshed",
            viewModel.uiState.value.errorMessage,
        )
      }

  @Test
  fun secondCreateAndCircleSwitchWhileRunningAreIgnored() =
      runTest(dispatcher) {
        profiles.result = Result.success(profile("circle-1", "circle-2"))
        circles =
            FakeCircleRepository(
                mapOf(
                    "circle-1" to circle("circle-1", "Arthur's family"),
                    "circle-2" to circle("circle-2", "Bea's family"),
                ),
            )
        val viewModel = viewModel()
        advanceUntilIdle()
        creation.pause = true

        viewModel.createInvitation()
        runCurrent()
        viewModel.createInvitation()
        viewModel.selectCircle("circle-2")
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

  private fun viewModel() =
      CreateInvitationViewModel(
          profileRepository = profiles,
          creationRepository = creation,
          lookupRepository = lookup,
          circleRepository = circles,
      )

  private fun profile(vararg circleIds: String) =
      UserProfile(
          uid = "caregiver-1",
          role = Role.CAREGIVER,
          person = Person(id = "caregiver-1", firstName = "Ada"),
          circleIds = circleIds.toList(),
      )

  private fun circle(id: String, name: String) = CareCircle(id = id, name = name)

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

  override suspend fun getCurrentProfile(): Result<UserProfile?> = result

  override suspend fun getProfile(uid: String): Result<UserProfile?> = result
}

private class FakeCircleRepository(private val circles: Map<String, CareCircle>) :
    CareCircleRepository {
  override suspend fun getCircle(circleId: String): Result<CareCircle> {
    val circle = circles[circleId] ?: return Result.failure(CareCircleError.NotFound)
    return Result.success(circle)
  }
}

private class FakeLookupRepository : InvitationLookupRepository {
  val requestedCircleIds = mutableListOf<String>()
  var results: Map<String, Result<List<InvitationSummary>>> = emptyMap()

  override suspend fun getInvitations(selectedCircleId: String): Result<List<InvitationSummary>> {
    requestedCircleIds += selectedCircleId
    return results[selectedCircleId] ?: Result.success(emptyList())
  }
}

private class FakeCreationRepository : InvitationCreationRepository {
  val requestedCircleIds = mutableListOf<String>()
  var result: InvitationCreationResult = InvitationCreationResult.Created("AB12CD34")
  var pause = false
  val release = CompletableDeferred<Unit>()

  override suspend fun createInvitation(selectedCircleId: String): InvitationCreationResult {
    requestedCircleIds += selectedCircleId
    if (pause) release.await()
    return result
  }
}
