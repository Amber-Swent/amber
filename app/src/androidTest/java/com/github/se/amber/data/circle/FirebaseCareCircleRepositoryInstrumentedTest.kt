// Written with assistance from OpenAI Codex.
package com.github.se.amber.data.circle

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.se.amber.data.onboarding.FirebaseOnboardingRepository
import com.github.se.amber.data.profile.FirebaseProfileRepository
import com.github.se.amber.e2e.FirebaseEmulator
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises [FirebaseCareCircleRepository.addPerson] against the Auth and Firestore emulators. */
@RunWith(AndroidJUnit4::class)
class FirebaseCareCircleRepositoryInstrumentedTest {
  @Before
  fun connectToEmulators() {
    val message = "Firebase emulators are not running on the host"
    val required =
        InstrumentationRegistry.getArguments().getString("requireFirebaseEmulators") == "true"
    if (required) assertTrue(message, FirebaseEmulator.isRunning())
    else assumeTrue(message, FirebaseEmulator.isRunning())
    FirebaseEmulator.useForDefaultApp()
  }

  @Test
  fun addPerson_createsPersonAndStoresItInCircle() = runBlocking {
    val circle = createCaregiverCircle("Ada")
    val circleDocument = Firebase.firestore.collection("careCircles").document(circle.id)
    val before =
        checkNotNull(circleDocument.get(Source.SERVER).await().toObject(CareCircle::class.java))
    val person = Person(firstName = "  Anne  ", lastName = "  Dupont  ", nickname = "  Nana  ")
    assertTrue(before.people.isEmpty())
    assertTrue(person.id.isBlank())

    val saved = FirebaseCareCircleRepository().addPerson(circle.id, person).getOrThrow()
    val after =
        checkNotNull(circleDocument.get(Source.SERVER).await().toObject(CareCircle::class.java))

    assertTrue(saved.id.isNotBlank())
    assertEquals("Anne", saved.firstName)
    assertEquals("Dupont", saved.lastName)
    assertEquals("Nana", saved.nickname)
    assertEquals(1, after.people.size)
    assertEquals(saved, after.people[saved.id])
    assertEquals(circle.memberIds, after.memberIds)
  }

  @Test
  fun addPerson_preservesPeopleAlreadyInCircle() = runBlocking {
    val circle = createCaregiverCircle("Ada")
    val repository = FirebaseCareCircleRepository()

    val first = repository.addPerson(circle.id, Person(firstName = "Anne")).getOrThrow()
    val second = repository.addPerson(circle.id, Person(firstName = "Bob")).getOrThrow()
    val circleDocument = Firebase.firestore.collection("careCircles").document(circle.id)
    val stored =
        checkNotNull(circleDocument.get(Source.SERVER).await().toObject(CareCircle::class.java))

    assertNotEquals(first.id, second.id)
    assertEquals(2, stored.people.size)
    assertEquals(first, stored.people[first.id])
    assertEquals(second, stored.people[second.id])
  }

  // Create the circle as a member, switches to a different signed-in user, and attempts the write
  @Test
  fun addPerson_deniesSignedInNonMember() = runBlocking {
    val circle = createCaregiverCircle("Ada")

    // Creating another Auth user signs the test in as that user.
    val email = "outsider-${UUID.randomUUID()}@amber.test"
    Firebase.auth.createUserWithEmailAndPassword(email, "Password123!").await()
    val outsiderId = checkNotNull(Firebase.auth.currentUser).uid
    assertTrue(outsiderId !in circle.memberIds)

    val result = FirebaseCareCircleRepository().addPerson(circle.id, Person(firstName = "Anne"))

    assertEquals(CareCircleError.PermissionDenied, result.exceptionOrNull())
  }

  private suspend fun createCaregiverCircle(firstName: String): CareCircle {
    val uniqueId = UUID.randomUUID().toString()
    Firebase.auth
        .createUserWithEmailAndPassword("person-$uniqueId@amber.test", "Password123!")
        .await()
    FirebaseProfileRepository()
        .createProfile(Person(firstName = firstName), Role.CAREGIVER)
        .getOrThrow()
    return FirebaseOnboardingRepository().createInitialCareCircle("Family $uniqueId").getOrThrow()
  }
}
