package com.github.se.amber.ui.patient.see_pictures

import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SeePicturesViewModelTest {

    private val mediaRepository = mockk<MediaRepository>()
    private val circleRepository = mockk<CareCircleRepository>()
    private val profileRepository = mockk<ProfileRepository>()

    private val profile =
        UserProfile(
            uid = "patient",
            role = Role.PATIENT,
            person = Person(),
            circleIds = listOf("circle1"),
        )
    private val circle = CareCircle(id = "circle1")

    private val picture1 = MediaItem.Picture(id = "p1", width = 400, height = 300)
    private val picture2 = MediaItem.Picture(id = "p2", width = 300, height = 400)
    private val audio = MediaItem.Audio(id = "a1")
    private val text = MediaItem.Text(id = "t1")

    @Before
    fun setUp() {
        // Unconfined: the coroutines launched in init run immediately, no need to advance time
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { profileRepository.getCurrentProfile() } returns Result.success(profile)
        coEvery { circleRepository.getCircle("circle1") } returns Result.success(circle)
        coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        SeePicturesViewModel(mediaRepository, circleRepository, profileRepository)

    @Test
    fun `loads the profile into the state`() {
        val viewModel = createViewModel()
        assertEquals(profile, viewModel.uiState.value.patientProfile)
        assertNull(viewModel.uiState.value.errorMsg)
    }

    @Test
    fun `loads the care circle once the profile is loaded`() {
        val viewModel = createViewModel()
        assertEquals(circle, viewModel.uiState.value.careCircle)
    }

    @Test
    fun `keeps all the media and only the pictures separately`() {
        coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
                flowOf(listOf(picture1, audio, picture2, text))

        val viewModel = createViewModel()

        assertEquals(listOf(picture1, audio, picture2, text), viewModel.uiState.value.allMediaItems)
        assertEquals(listOf(picture1, picture2), viewModel.uiState.value.allPictures)
    }

    @Test
    fun `pictures list is empty when there is no media`() {
        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value.allPictures.isEmpty())
    }

    @Test
    fun `sets an error when no profile is found`() {
        coEvery { profileRepository.getCurrentProfile() } returns Result.success(null)

        val viewModel = createViewModel()

        assertEquals("No profile found.", viewModel.uiState.value.errorMsg)
    }

    @Test
    fun `sets an error when the profile fails to load`() {
        coEvery { profileRepository.getCurrentProfile() } returns
                Result.failure(Exception("boom"))

        val viewModel = createViewModel()

        assertEquals("Failed to load the profile: boom", viewModel.uiState.value.errorMsg)
    }

    @Test
    fun `sets an error when the care circle fails to load`() {
        coEvery { circleRepository.getCircle("circle1") } returns
                Result.failure(Exception("circle error"))

        val viewModel = createViewModel()

        assertEquals("Failed to load the care circle: circle error", viewModel.uiState.value.errorMsg)
    }

    @Test
    fun `sets an error when observing the media fails`() {
        coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
                flow { throw RuntimeException("media error") }

        val viewModel = createViewModel()

        assertEquals("Failed to load media: media error", viewModel.uiState.value.errorMsg)
    }

    @Test
    fun `clearErrorMsg resets the error`() {
        coEvery { profileRepository.getCurrentProfile() } returns Result.success(null)
        val viewModel = createViewModel()
        assertEquals("No profile found.", viewModel.uiState.value.errorMsg)

        viewModel.clearErrorMsg()

        assertNull(viewModel.uiState.value.errorMsg)
    }

    @Test
    fun `does not observe media while the profile has no circle`() {
        // profile never arrives: the state keeps circleIds = emptyList()
        coEvery { profileRepository.getCurrentProfile() } returns Result.success(null)

        createViewModel()

        coVerify(exactly = 0) { mediaRepository.observeApprovedMedia(any()) }
    }
}