// this code was written with the aid of AI
package com.github.se.amber.ui.roleLoading

import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.google.android.gms.tasks.TaskCompletionSource
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
import kotlinx.coroutines.test.runCurrent
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

  private fun mockSnapshot(profile: UserProfile?, hasRoleField: Boolean = true): DocumentSnapshot {
    val snapshot = mockk<DocumentSnapshot>()
    every { snapshot.toObject(UserProfile::class.java) } returns profile
    every { snapshot.contains("role") } returns hasRoleField
    return snapshot
  }

  /** Makes Firestore return a document with the given profile / "role" field presence. */
  private fun stubDocument(profile: UserProfile?, hasRoleField: Boolean = true) {
    every { document.get() } returns Tasks.forResult(mockSnapshot(profile, hasRoleField))
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
  }

  @Test
  fun loadRole_userNotConnected_doesNotQueryFirestore() = runTest {
    every { auth.currentUser } returns null

    viewModel.loadRole()
    advanceUntilIdle()

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
    // The Firestore task stays pending, so the first call is guaranteed to be in flight.
    val pending = TaskCompletionSource<DocumentSnapshot>()
    every { document.get() } returns pending.task

    viewModel.loadRole()
    runCurrent() // the first call starts and suspends on the pending task
    viewModel.loadRole() // happens while the first call is still in flight
    runCurrent()

    verify(exactly = 1) { firestore.collection("users") }
    assertEquals(RoleState.Loading, viewModel.state.value)

    // Let the first call finish
    pending.setResult(mockSnapshot(UserProfile(uid = uid, role = Role.PATIENT)))
    advanceUntilIdle()

    assertEquals(RoleState.Loaded(Role.PATIENT), viewModel.state.value)
  }

  @Test
  fun loadRole_retryAfterError_emitsLoadingThenCanSucceed() = runTest {
    every { document.get() } returns Tasks.forException(RuntimeException("Firestore down"))
    viewModel.loadRole()
    advanceUntilIdle()
    assertEquals(RoleState.Error, viewModel.state.value)

    val pending = TaskCompletionSource<DocumentSnapshot>()
    every { document.get() } returns pending.task
    viewModel.loadRole()
    runCurrent()
    assertEquals(RoleState.Loading, viewModel.state.value)

    pending.setResult(mockSnapshot(UserProfile(uid = uid, role = Role.CAREGIVER)))
    advanceUntilIdle()
    assertEquals(RoleState.Loaded(Role.CAREGIVER), viewModel.state.value)
  }

  @Test
  fun signOut_callsFirebaseAuthSignOut() {
    viewModel.signOut()

    verify(exactly = 1) { auth.signOut() }
  }
}
