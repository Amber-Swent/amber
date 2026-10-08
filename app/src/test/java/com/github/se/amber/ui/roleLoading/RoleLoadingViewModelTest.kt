// this code was written with the aid of AI
package com.github.se.amber.ui.roleLoading

import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoleLoadingViewModelTest {

  private val uid = "uid123"

  private lateinit var auth: FirebaseAuth
  private lateinit var firestore: FirebaseFirestore
  private lateinit var collection: CollectionReference
  private lateinit var document: DocumentReference
  private lateinit var user: FirebaseUser
  private lateinit var viewModel: RoleLoadingViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher())

    auth = mockk(relaxed = true)
    firestore = mockk()
    collection = mockk()
    document = mockk()
    user = mockk()

    every { auth.currentUser } returns user
    every { user.uid } returns uid
    every { firestore.collection("users") } returns collection
    every { collection.document(uid) } returns document

    viewModel = RoleLoadingViewModel(auth, firestore)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkAll()
  }

  /** Makes Firestore return a document with the given profile / "role" field presence. */
  private fun stubDocument(profile: UserProfile?, hasRoleField: Boolean = true) {
    val snapshot = mockk<DocumentSnapshot>()
    every { snapshot.toObject(UserProfile::class.java) } returns profile
    every { snapshot.contains("role") } returns hasRoleField
    every { document.get() } returns Tasks.forResult(snapshot)
  }

  @Test
  fun initialState_isLoading() {
    assertEquals(RoleState.Loading, viewModel.state.value)
  }

  @Test
  fun loadRole_userNotConnected_emitsError() = runTest {
    every { auth.currentUser } returns null

    viewModel.loadRole()
    advanceUntilIdle()

    assertEquals(RoleState.Error, viewModel.state.value)
    verify(exactly = 0) { firestore.collection(any()) }
  }

  @Test
  fun loadRole_patientProfile_emitsLoadedPatient() = runTest {
    stubDocument(UserProfile(uid = uid, role = Role.PATIENT))

    viewModel.loadRole()
    advanceUntilIdle()

    assertEquals(RoleState.Loaded(Role.PATIENT), viewModel.state.value)
  }

  @Test
  fun loadRole_caregiverProfile_emitsLoadedCaregiver() = runTest {
    stubDocument(UserProfile(uid = uid, role = Role.CAREGIVER))

    viewModel.loadRole()
    advanceUntilIdle()

    assertEquals(RoleState.Loaded(Role.CAREGIVER), viewModel.state.value)
  }

  @Test
  fun loadRole_noUserDocument_emitsNoProfile() = runTest {
    stubDocument(profile = null)

    viewModel.loadRole()
    advanceUntilIdle()

    assertEquals(RoleState.NoProfile, viewModel.state.value)
  }

  @Test
  fun loadRole_roleFieldMissing_emitsError() = runTest {
    // the profile exists but "role" is absent: must not silently fall back to the default role
    stubDocument(UserProfile(uid = uid), hasRoleField = false)

    viewModel.loadRole()
    advanceUntilIdle()

    assertEquals(RoleState.Error, viewModel.state.value)
  }

  @Test
  fun loadRole_firestoreFails_emitsError() = runTest {
    every { document.get() } returns Tasks.forException(RuntimeException("Firestore down"))

    viewModel.loadRole()
    advanceUntilIdle()

    assertEquals(RoleState.Error, viewModel.state.value)
  }

  @Test
  fun loadRole_calledTwiceWhileLoading_secondCallIsIgnored() = runTest {
    stubDocument(UserProfile(uid = uid, role = Role.PATIENT))

    viewModel.loadRole()
    viewModel.loadRole() // the first job has not finished yet
    advanceUntilIdle()

    verify(exactly = 1) { firestore.collection("users") }
    assertEquals(RoleState.Loaded(Role.PATIENT), viewModel.state.value)
  }

  @Test
  fun loadRole_retryAfterError_canSucceed() = runTest {
    every { document.get() } returns Tasks.forException(RuntimeException("Firestore down"))
    viewModel.loadRole()
    advanceUntilIdle()
    assertEquals(RoleState.Error, viewModel.state.value)

    stubDocument(UserProfile(uid = uid, role = Role.CAREGIVER))
    viewModel.loadRole()
    advanceUntilIdle()

    assertEquals(RoleState.Loaded(Role.CAREGIVER), viewModel.state.value)
  }

  @Test
  fun signOut_callsFirebaseAuthSignOut() {
    viewModel.signOut()

    verify(exactly = 1) { auth.signOut() }
  }
}
