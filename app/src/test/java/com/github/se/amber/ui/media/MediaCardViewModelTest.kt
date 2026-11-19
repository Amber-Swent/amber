// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.media

import com.github.se.amber.R
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.circle.ActiveCircleRepositoryProvider
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.UserProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric is only here so that android.util.Log.e (called by the ViewModel on errors) works
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MediaCardViewModelTest {

  private val profileRepository = mockk<ProfileRepository>()
  private val circleRepository = mockk<CareCircleRepository>()
  private val activeCircleRepository = mockk<ActiveCircleRepository>()
  private lateinit var viewModel: MediaCardViewModel

  private val authorProfile = userProfile("author", "Alice")
  private val currentProfile = userProfile("me", "Patient", listOf("circle1"))

  private fun userProfile(
      uid: String,
      firstName: String,
      circleIds: List<String> = emptyList(),
      nickname: String? = null,
  ) =
      UserProfile(
          uid = uid,
          person = Person(id = uid, firstName = firstName, nickname = nickname),
          circleIds = circleIds,
      )

  private fun circleWith(vararg people: Pair<String, String>) =
      CareCircle(
          id = "circle1",
          people = people.associate { (id, name) -> id to Person(id = id, firstName = name) },
      )

  private fun media(
      id: String = "m1",
      authorId: String = "author",
      shownIds: List<String> = emptyList(),
  ) = MediaItem.Picture(id = id, authorId = authorId, shownIds = shownIds)

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    coEvery { profileRepository.getProfile("author") } returns Result.success(authorProfile)
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(currentProfile)
    every { activeCircleRepository.activeCircleId(any(), any()) } answers
        {
          flowOf(secondArg<List<String>>().firstOrNull { it.isNotBlank() })
        }
    coEvery { circleRepository.getCircle("circle1") } returns Result.success(circleWith())
    viewModel = MediaCardViewModel(profileRepository, circleRepository, activeCircleRepository)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  // ---------- initial state ----------

  @Test
  fun initialState_isLoadingWithEmptyData() {
    val state = viewModel.uiState.value

    assertEquals("", state.addedByName)
    assertTrue(state.relatedPersons.isEmpty())
    assertTrue(state.isLoading)
    assertNull(state.errorMsgRes)
  }

  @Test
  fun defaultActiveCircleProviderIsUsedWhenRepositoryIsNotInjected() {
    val provider = ActiveCircleRepositoryProvider.default
    mockkObject(provider)
    try {
      every { provider.repository } returns activeCircleRepository
      coEvery { circleRepository.getCircle("circle1") } returns
          Result.success(circleWith("p1" to "Bob"))

      MediaCardViewModel(profileRepository, circleRepository).load(media(shownIds = listOf("p1")))

      verify(exactly = 1) { activeCircleRepository.activeCircleId("me", listOf("circle1")) }
      coVerify(exactly = 1) { circleRepository.getCircle("circle1") }
    } finally {
      unmockkObject(provider)
    }
  }

  // ---------- happy paths ----------

  @Test
  fun load_withoutShownIds_setsAuthorAndSkipsCircleLookup() {
    viewModel.load(media())

    val state = viewModel.uiState.value
    assertEquals("Alice", state.addedByName)
    assertFalse(state.isLoading)
    assertTrue(state.relatedPersons.isEmpty())
    assertNull(state.errorMsgRes)
    coVerify(exactly = 0) { profileRepository.getCurrentProfile() }
    coVerify(exactly = 0) { circleRepository.getCircle(any()) }
  }

  @Test
  fun load_authorWithNickname_usesNickname() {
    coEvery { profileRepository.getProfile("author") } returns
        Result.success(userProfile("author", "Alice", nickname = "Lili"))

    viewModel.load(media())

    assertEquals("Lili", viewModel.uiState.value.addedByName)
  }

  @Test
  fun load_authorWithBlankNickname_usesFirstName() {
    coEvery { profileRepository.getProfile("author") } returns
        Result.success(userProfile("author", "Alice", nickname = "  "))

    viewModel.load(media())

    assertEquals("Alice", viewModel.uiState.value.addedByName)
  }

  @Test
  fun load_withShownIds_resolvesRelatedPersonsFromCircle() {
    coEvery { circleRepository.getCircle("circle1") } returns
        Result.success(circleWith("p1" to "Bob", "p2" to "Carol"))

    viewModel.load(media(shownIds = listOf("p1", "p2")))

    val state = viewModel.uiState.value
    assertEquals("Alice", state.addedByName)
    assertEquals(listOf("Bob", "Carol"), state.relatedPersons.map { it.firstName })
    assertFalse(state.isLoading)
    assertNull(state.errorMsgRes)
  }

  @Test
  fun load_shownIdNotInCircle_fallsBackToMemberProfile() {
    coEvery { circleRepository.getCircle("circle1") } returns
        Result.success(circleWith("p1" to "Bob"))
    coEvery { profileRepository.getProfile("member") } returns
        Result.success(userProfile("member", "Dave"))

    viewModel.load(media(shownIds = listOf("p1", "member")))

    val persons = viewModel.uiState.value.relatedPersons
    assertEquals(listOf("Bob", "Dave"), persons.map { it.firstName })
    // the ids are what the button passes to the navigation callback
    assertEquals(listOf("p1", "member"), persons.map { it.id })
  }

  @Test
  fun load_unresolvableShownIds_areIgnoredWithoutError() {
    coEvery { profileRepository.getProfile("ghost") } returns Result.failure(Exception("nope"))
    coEvery { profileRepository.getProfile("missing") } returns Result.success(null)

    viewModel.load(media(shownIds = listOf("ghost", "missing")))

    assertTrue(viewModel.uiState.value.relatedPersons.isEmpty())
    assertNull(viewModel.uiState.value.errorMsgRes)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun load_usesFirstCircleOfCurrentProfile() {
    coEvery { profileRepository.getCurrentProfile() } returns
        Result.success(userProfile("me", "Patient", listOf("circle1", "circle2")))
    coEvery { circleRepository.getCircle("circle1") } returns
        Result.success(circleWith("p1" to "Bob"))

    viewModel.load(media(shownIds = listOf("p1")))

    assertEquals(listOf("Bob"), viewModel.uiState.value.relatedPersons.map { it.firstName })
    coVerify(exactly = 0) { circleRepository.getCircle("circle2") }
  }

  // ---------- error paths ----------

  @Test
  fun load_authorFetchFails_setsErrorAndStopsLoading() {
    coEvery { profileRepository.getProfile("author") } returns Result.failure(Exception("boom"))

    viewModel.load(media())

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertNotNull(state.errorMsgRes)
    assertEquals(R.string.mediaCardErrorLoadAuthor, state.errorMsgRes)
    assertEquals("", state.addedByName)
  }

  @Test
  fun load_authorNotFound_setsError() {
    coEvery { profileRepository.getProfile("author") } returns Result.success(null)

    viewModel.load(media())

    assertEquals(R.string.mediaCardErrorAuthorNotFound, viewModel.uiState.value.errorMsgRes)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun load_currentProfileFails_setsErrorButKeepsAuthor() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.failure(Exception("boom"))

    viewModel.load(media(shownIds = listOf("p1")))

    val state = viewModel.uiState.value
    assertEquals(R.string.mediaCardErrorLoadProfile, state.errorMsgRes)
    assertEquals("Alice", state.addedByName)
    assertFalse(state.isLoading)
  }

  @Test
  fun load_currentProfileNull_setsNoCircleError() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(null)

    viewModel.load(media(shownIds = listOf("p1")))

    assertEquals(R.string.mediaCardErrorNoCircle, viewModel.uiState.value.errorMsgRes)
  }

  @Test
  fun load_currentProfileWithoutCircle_setsNoCircleError() {
    coEvery { profileRepository.getCurrentProfile() } returns
        Result.success(userProfile("me", "Patient", emptyList()))

    viewModel.load(media(shownIds = listOf("p1")))

    assertEquals(R.string.mediaCardErrorNoCircle, viewModel.uiState.value.errorMsgRes)
    coVerify(exactly = 0) { circleRepository.getCircle(any()) }
  }

  @Test
  fun load_circleFetchFails_setsErrorButKeepsAuthor() {
    coEvery { circleRepository.getCircle("circle1") } returns Result.failure(Exception("boom"))

    viewModel.load(media(shownIds = listOf("p1")))

    val state = viewModel.uiState.value
    assertEquals(R.string.mediaCardErrorLoadCircle, state.errorMsgRes)
    assertEquals("Alice", state.addedByName)
    assertTrue(state.relatedPersons.isEmpty())
  }

  // ---------- clearErrorMsg ----------

  @Test
  fun clearErrorMsg_resetsErrorToNull() {
    coEvery { profileRepository.getProfile("author") } returns Result.success(null)
    viewModel.load(media())
    assertNotNull(viewModel.uiState.value.errorMsgRes)

    viewModel.clearErrorMsg()

    assertNull(viewModel.uiState.value.errorMsgRes)
  }

  @Test
  fun clearErrorMsg_keepsOtherFields() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.failure(Exception("boom"))
    viewModel.load(media(shownIds = listOf("p1")))

    viewModel.clearErrorMsg()

    assertEquals("Alice", viewModel.uiState.value.addedByName)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  // ---------- reload / cancellation ----------

  @Test
  fun load_resetsPreviousErrorAndData() {
    coEvery { profileRepository.getProfile("author") } returns Result.success(null)
    viewModel.load(media())
    assertEquals(R.string.mediaCardErrorAuthorNotFound, viewModel.uiState.value.errorMsgRes)

    coEvery { profileRepository.getProfile("author") } returns Result.success(authorProfile)
    viewModel.load(media())

    assertNull(viewModel.uiState.value.errorMsgRes)
    assertEquals("Alice", viewModel.uiState.value.addedByName)
  }

  @Test
  fun load_whilePending_isLoadingThenDone() {
    val gate = CompletableDeferred<Unit>()
    coEvery { profileRepository.getProfile("author") } coAnswers
        {
          gate.await()
          Result.success(authorProfile)
        }

    viewModel.load(media())

    assertTrue(viewModel.uiState.value.isLoading)
    assertEquals("", viewModel.uiState.value.addedByName)

    gate.complete(Unit)

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals("Alice", viewModel.uiState.value.addedByName)
  }

  @Test
  fun load_secondCallCancelsFirstOne() {
    val gate = CompletableDeferred<Unit>()
    coEvery { profileRepository.getProfile("author") } coAnswers
        {
          gate.await()
          Result.success(authorProfile)
        }
    coEvery { profileRepository.getProfile("author2") } returns
        Result.success(userProfile("author2", "Zoe"))

    viewModel.load(media(id = "m1", authorId = "author")) // suspended on the gate
    viewModel.load(media(id = "m2", authorId = "author2")) // cancels the first, completes now
    assertEquals("Zoe", viewModel.uiState.value.addedByName)

    gate.complete(Unit) // the cancelled first load must not overwrite the state

    assertEquals("Zoe", viewModel.uiState.value.addedByName)
    assertFalse(viewModel.uiState.value.isLoading)
  }
}
