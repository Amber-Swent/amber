// Written by Alaabrine, with assistance from
// Cursor (Composer).
package com.github.se.amber.firestore

import com.github.se.amber.model.appointment.Appointment
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.circle.Invitation
import com.github.se.amber.model.location.Place
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaStatus
import com.github.se.amber.model.story.Story
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit and integration coverage for the behaviour encoded in `firebase/firestore/firestore.rules`.
 *
 * Helpers are exercised with a [MockDocumentLookup] (caller-controlled returns). Collection
 * allow/deny paths are exercised end-to-end against a [FakeFirestore].
 *
 * For checks against the real rules file on the Firestore emulator, see
 * [FirestoreSecurityRulesEmulatorTest].
 */
class FirestoreSecurityRulesTest {

  // ---------------------------------------------------------------------------
  // Unit tests — helper logic with mocks
  // ---------------------------------------------------------------------------

  @Test
  fun isSignedIn_isFalse_whenAuthMissing() {
    val rules = FirestoreSecurityRules(authUid = null, lookup = MockDocumentLookup())
    assertFalse(rules.isSignedIn())
  }

  @Test
  fun isSelf_isTrue_onlyForMatchingUid() {
    val rules = FirestoreSecurityRules(authUid = "u1", lookup = MockDocumentLookup())
    assertTrue(rules.isSelf("u1"))
    assertFalse(rules.isSelf("u2"))
  }

  @Test
  fun isMember_usesMockedCircleMembership() {
    val lookup =
        MockDocumentLookup(
            circleOf = { id ->
              if (id == "c1") CareCircle(id = "c1", memberIds = listOf("u1")) else null
            },
        )
    val asMember = FirestoreSecurityRules(authUid = "u1", lookup = lookup)
    val asStranger = FirestoreSecurityRules(authUid = "u2", lookup = lookup)

    assertTrue(asMember.isMember("c1"))
    assertFalse(asStranger.isMember("c1"))
    assertFalse(asMember.isMember("missing"))
  }

  @Test
  fun isCaregiver_readsMockedUserRole() {
    val lookup =
        MockDocumentLookup(
            userOf = { uid ->
              when (uid) {
                "care" -> UserProfile(uid = "care", role = Role.CAREGIVER)
                "pat" -> UserProfile(uid = "pat", role = Role.PATIENT)
                else -> null
              }
            },
        )
    assertTrue(FirestoreSecurityRules(authUid = "care", lookup = lookup).isCaregiver())
    assertFalse(FirestoreSecurityRules(authUid = "pat", lookup = lookup).isCaregiver())
    assertFalse(FirestoreSecurityRules(authUid = "ghost", lookup = lookup).isCaregiver())
  }

  @Test
  fun sharesCircleWith_requiresOverlapOnMockedCircleIds() {
    val lookup =
        MockDocumentLookup(
            userOf = { uid ->
              if (uid == "viewer") UserProfile(uid = "viewer", circleIds = listOf("c1", "c2"))
              else null
            },
        )
    val rules = FirestoreSecurityRules(authUid = "viewer", lookup = lookup)
    assertTrue(rules.sharesCircleWith(UserProfile(uid = "other", circleIds = listOf("c2"))))
    assertFalse(rules.sharesCircleWith(UserProfile(uid = "other", circleIds = listOf("c9"))))
  }

  @Test
  fun onlyOwnNicknameChanged_allowsSelfKeyOnly() {
    val rules = FirestoreSecurityRules(authUid = "u1", lookup = MockDocumentLookup())
    val before = mapOf("u1" to mapOf("p1" to "Alice"), "u2" to mapOf("p1" to "Bob"))
    assertTrue(rules.onlyOwnNicknameChanged(before, before))
    assertTrue(
        rules.onlyOwnNicknameChanged(
            before,
            before + ("u1" to mapOf("p1" to "Ally")),
        ),
    )
    assertFalse(
        rules.onlyOwnNicknameChanged(
            before,
            before + ("u2" to mapOf("p1" to "Bobby")),
        ),
    )
  }

  @Test
  fun placesChangedOnlyByCaregiver_defersToMockedRole() {
    val caregiverLookup =
        MockDocumentLookup(userOf = { UserProfile(uid = it, role = Role.CAREGIVER) })
    val patientLookup = MockDocumentLookup(userOf = { UserProfile(uid = it, role = Role.PATIENT) })
    val before = emptyMap<String, Any>()
    val after = mapOf("home" to "Home")

    assertTrue(
        FirestoreSecurityRules(authUid = "c", lookup = caregiverLookup)
            .placesChangedOnlyByCaregiver(before, after),
    )
    assertFalse(
        FirestoreSecurityRules(authUid = "p", lookup = patientLookup)
            .placesChangedOnlyByCaregiver(before, after),
    )
    assertTrue(
        FirestoreSecurityRules(authUid = "p", lookup = patientLookup)
            .placesChangedOnlyByCaregiver(before, before),
    )
  }

  @Test
  fun mediaStoragePathMatches_requiresCirclePrefix() {
    val rules = FirestoreSecurityRules(authUid = "u1", lookup = MockDocumentLookup())
    assertTrue(rules.mediaStoragePathMatches("c1", "careCircles/c1/media/m1.jpg"))
    assertFalse(rules.mediaStoragePathMatches("c1", "careCircles/other/media/m1.jpg"))
    assertFalse(rules.mediaStoragePathMatches("c1", "elsewhere/m1.jpg"))
  }

  // ---------------------------------------------------------------------------
  // Integration tests — full allow/deny paths with FakeFirestore
  // ---------------------------------------------------------------------------

  @Test
  fun users_readCreateUpdate_enforcedByFakeStore() {
    val db = FakeFirestore()
    val care =
        UserProfile(
            uid = "care",
            role = Role.CAREGIVER,
            person = Person(id = "care", firstName = "Ada"),
            circleIds = listOf("c1"),
        )
    val peer =
        UserProfile(
            uid = "peer",
            role = Role.CAREGIVER,
            person = Person(id = "peer", firstName = "Bob"),
            circleIds = listOf("c1"),
        )
    val stranger =
        UserProfile(
            uid = "stranger",
            role = Role.CAREGIVER,
            person = Person(id = "stranger", firstName = "Eve"),
            circleIds = listOf("c9"),
        )
    db.putUser(care)
    db.putUser(peer)
    db.putUser(stranger)
    db.putCircle(CareCircle(id = "c1", memberIds = listOf("care", "peer"), createdBy = "care"))

    assertTrue(db.rulesFor("care").canReadUser(care))
    assertTrue(db.rulesFor("care").canReadUser(peer))
    assertFalse(db.rulesFor("stranger").canReadUser(care))

    val onboard =
        UserProfile(
            uid = "new",
            role = Role.CAREGIVER,
            person = Person(id = "new", firstName = "New"),
            circleIds = emptyList(),
        )
    assertTrue(db.rulesFor("new").canCreateUser(onboard))
    assertFalse(db.rulesFor("new").canCreateUser(onboard.copy(uid = "other")))

    db.putUser(onboard)
    val personOnly =
        onboard.copy(person = Person(id = "new", firstName = "Newer"))
    assertTrue(db.rulesFor("new").canUpdateUser(onboard, personOnly))
    assertFalse(
        db.rulesFor("new")
            .canUpdateUser(onboard, onboard.copy(circleIds = listOf("c1"))),
    )
    assertFalse(db.rulesFor("new").canUpdateUser(onboard, personOnly.copy(role = Role.PATIENT)))
    assertFalse(db.rulesFor("new").canDeleteUser())
  }

  @Test
  fun careCircles_createAndMemberEdit_enforcedByFakeStore() {
    val db = FakeFirestore()
    val careProfile = UserProfile(uid = "care", role = Role.CAREGIVER, person = Person(id = "care"))
    db.putUser(careProfile)

    val created =
        CareCircle(
            id = "c1",
            name = "Family",
            patientId = "",
            memberIds = listOf("care"),
            createdBy = "care",
            createdAt = 1L,
        )
    assertTrue(db.rulesFor("care").canCreateCircle(created))
    assertFalse(
        db.rulesFor("care").canCreateCircle(created.copy(memberIds = listOf("care", "extra"))),
    )

    db.putCircle(created)
    db.putUser(UserProfile(uid = "pat", role = Role.PATIENT, person = Person(id = "pat")))

    // Client-side join is denied; membership sync is backend-only (PR #83).
    val joined = created.copy(memberIds = listOf("care", "pat"), patientId = "pat")
    assertFalse(db.rulesFor("pat").canUpdateCircle(created, joined))

    db.putCircle(joined)
    val nicknameEdit = joined.copy(nicknames = mapOf("pat" to mapOf("care" to "Dad")))
    assertTrue(db.rulesFor("pat").canUpdateCircle(joined, nicknameEdit))

    val placesEdit = joined.copy(places = mapOf("home" to Place(id = "home", name = "Home")))
    assertFalse(db.rulesFor("pat").canUpdateCircle(joined, placesEdit))
    assertTrue(db.rulesFor("care").canUpdateCircle(joined, placesEdit))
    assertFalse(db.rulesFor("care").canDeleteCircle())
  }

  @Test
  fun mediaStoriesAppointments_memberAndRoleGates_enforcedByFakeStore() {
    val db = FakeFirestore()
    db.putUser(UserProfile(uid = "care", role = Role.CAREGIVER, person = Person(id = "care")))
    db.putUser(UserProfile(uid = "pat", role = Role.PATIENT, person = Person(id = "pat")))
    db.putUser(
        UserProfile(uid = "outsider", role = Role.CAREGIVER, person = Person(id = "outsider"))
    )
    db.putCircle(CareCircle(id = "c1", memberIds = listOf("care", "pat"), createdBy = "care"))

    val picture =
        MediaItem.Picture(
            id = "m1",
            authorId = "pat",
            storagePath = "careCircles/c1/media/m1.jpg",
            status = MediaStatus.APPROVED,
        )
    assertTrue(db.rulesFor("pat").canCreateMedia("c1", picture))
    assertFalse(
        db.rulesFor("pat")
            .canCreateMedia(
                "c1",
                picture.copy(storagePath = "careCircles/other/media/m1.jpg"),
            ),
    )
    assertFalse(db.rulesFor("outsider").canCreateMedia("c1", picture.copy(authorId = "outsider")))

    db.putMedia("c1", picture)
    assertTrue(
        db.rulesFor("pat")
            .canUpdateMedia(
                "c1",
                picture,
                picture.copy(description = "beach"),
            ),
    )
    assertFalse(
        db.rulesFor("care")
            .canUpdateMedia(
                "c1",
                picture,
                picture.copy(description = "nope"),
            ),
    )
    assertTrue(db.rulesFor("pat").canDeleteMedia("c1", picture))
    assertFalse(db.rulesFor("care").canDeleteMedia("c1", picture))

    val story = Story(id = "s1", title = "Trip", createdBy = "care")
    assertTrue(db.rulesFor("pat").canWriteStory("c1", story))
    assertFalse(db.rulesFor("outsider").canWriteStory("c1", story))
    assertTrue(db.rulesFor("pat").canDeleteStory("c1"))

    val appointment = Appointment(id = "a1", title = "Doctor", createdBy = "care")
    assertTrue(db.rulesFor("care").canWriteAppointment("c1", appointment))
    assertFalse(db.rulesFor("pat").canWriteAppointment("c1", appointment))
    assertTrue(db.rulesFor("care").canDeleteAppointment("c1"))
    assertFalse(db.rulesFor("pat").canDeleteAppointment("c1"))
  }

  @Test
  fun invitations_getCreate_denyClientRedeem_enforcedByFakeStore() {
    val db = FakeFirestore()
    db.putUser(UserProfile(uid = "care", role = Role.CAREGIVER, person = Person(id = "care")))
    db.putUser(UserProfile(uid = "pat", role = Role.PATIENT, person = Person(id = "pat")))
    db.putCircle(CareCircle(id = "c1", memberIds = listOf("care"), createdBy = "care"))

    val now = 1_000_000L
    val invite =
        Invitation(
            code = "ABC123",
            circleId = "c1",
            role = Role.PATIENT,
            createdBy = "care",
            createdAt = now - 10,
            expiresAt = now + 60_000,
            usedBy = null,
        )

    assertTrue(db.rulesFor("care", now).canGetInvitation())
    assertFalse(db.rulesFor("care", now).canListInvitations())
    assertTrue(db.rulesFor("care", now).canCreateInvitation(invite))
    assertFalse(db.rulesFor("pat", now).canCreateInvitation(invite.copy(createdBy = "pat")))

    db.putInvitation(invite)
    val redeemed = invite.copy(usedBy = "pat")
    // Redeem is backend-only (Admin SDK / Cloud Functions in PR #83).
    assertFalse(db.rulesFor("pat", now).canUpdateInvitation(invite, redeemed))
    assertFalse(db.rulesFor("care", now).canDeleteInvitation())
  }
}

/** Caller-controlled document lookups for helper unit tests. */
private class MockDocumentLookup(
    private val userOf: (String) -> UserProfile? = { null },
    private val circleOf: (String) -> CareCircle? = { null },
    private val userAfterOf: (String) -> UserProfile? = userOf,
    private val circleAfterOf: (String) -> CareCircle? = circleOf,
) : DocumentLookup {
  override fun user(uid: String) = userOf(uid)

  override fun circle(circleId: String) = circleOf(circleId)

  override fun userAfter(uid: String) = userAfterOf(uid)

  override fun circleAfter(circleId: String) = circleAfterOf(circleId)
}

/** In-memory Firestore stand-in for end-to-end rule evaluation. */
private class FakeFirestore {
  private val users = mutableMapOf<String, UserProfile>()
  private val circles = mutableMapOf<String, CareCircle>()
  private val media = mutableMapOf<Pair<String, String>, MediaItem>()
  private val invitations = mutableMapOf<String, Invitation>()

  /** Pending batch writes visible to getAfter/existsAfter during onboarding-style creates. */
  private val pendingUsers = mutableMapOf<String, UserProfile>()
  private val pendingCircles = mutableMapOf<String, CareCircle>()

  fun putUser(profile: UserProfile) {
    users[profile.uid] = profile
  }

  fun putCircle(circle: CareCircle) {
    circles[circle.id] = circle
  }

  fun putMedia(circleId: String, item: MediaItem) {
    media[circleId to item.id] = item
  }

  fun putInvitation(invitation: Invitation) {
    invitations[invitation.code] = invitation
  }

  fun stageUser(profile: UserProfile) {
    pendingUsers[profile.uid] = profile
  }

  fun stageCircle(circle: CareCircle) {
    pendingCircles[circle.id] = circle
  }

  fun rulesFor(uid: String?, @Suppress("UNUSED_PARAMETER") nowMillis: Long = 0L): FirestoreSecurityRules =
      FirestoreSecurityRules(
          authUid = uid,
          lookup =
              object : DocumentLookup {
                override fun user(uid: String) = users[uid]

                override fun circle(circleId: String) = circles[circleId]

                override fun userAfter(uid: String) = pendingUsers[uid] ?: users[uid]

                override fun circleAfter(circleId: String) =
                    pendingCircles[circleId] ?: circles[circleId]
              },
      )
}

private interface DocumentLookup {
  fun user(uid: String): UserProfile?

  fun circle(circleId: String): CareCircle?

  fun userAfter(uid: String): UserProfile?

  fun circleAfter(circleId: String): CareCircle?
}

/**
 * Kotlin mirror of `firebase/firestore/firestore.rules` allow conditions. Kept in the test source
 * set so the rules file stays the single production change.
 */
private class FirestoreSecurityRules(
    private val authUid: String?,
    private val lookup: DocumentLookup,
) {
  fun isSignedIn(): Boolean = authUid != null

  fun isSelf(uid: String): Boolean = isSignedIn() && authUid == uid

  fun isMember(circleId: String): Boolean {
    val circle = lookup.circle(circleId) ?: return false
    return isSignedIn() && authUid in circle.memberIds
  }

  fun isCaregiver(): Boolean {
    val user = authUid?.let(lookup::user) ?: return false
    return isSignedIn() && user.role == Role.CAREGIVER
  }

  fun isCaregiverAfter(): Boolean {
    val user = authUid?.let(lookup::userAfter) ?: return false
    return isSignedIn() && user.role == Role.CAREGIVER
  }

  fun sharesCircleWith(resource: UserProfile): Boolean {
    val viewer = authUid?.let(lookup::user) ?: return false
    return isSignedIn() && viewer.circleIds.any { it in resource.circleIds }
  }

  fun onlyOwnNicknameChanged(
      before: Map<String, Map<String, String>>,
      after: Map<String, Map<String, String>>,
  ): Boolean {
    if (before == after) return true
    val uid = authUid ?: return false
    val affected = (before.keys + after.keys).filter { key -> before[key] != after[key] }.toSet()
    return affected == setOf(uid)
  }

  fun placesChangedOnlyByCaregiver(before: Map<*, *>, after: Map<*, *>): Boolean =
      before == after || isCaregiver()

  fun mediaStoragePathMatches(circleId: String, storagePath: String): Boolean =
      storagePath.matches(Regex("^careCircles/$circleId/.*"))

  fun memberCircleFieldEdit(circleId: String, before: CareCircle, after: CareCircle): Boolean =
      isMember(circleId) &&
          after.id == before.id &&
          after.createdBy == before.createdBy &&
          after.createdAt == before.createdAt &&
          after.memberIds == before.memberIds &&
          after.patientId == before.patientId &&
          onlyOwnNicknameChanged(before.nicknames, after.nicknames) &&
          placesChangedOnlyByCaregiver(before.places, after.places)

  fun canReadUser(resource: UserProfile): Boolean =
      isSelf(resource.uid) || sharesCircleWith(resource)

  fun canCreateUser(resource: UserProfile): Boolean {
    if (!isSelf(resource.uid) || resource.uid != authUid) return false
    return when (resource.circleIds.size) {
      0 -> true
      1 -> {
        val circle = lookup.circleAfter(resource.circleIds[0]) ?: return false
        authUid in circle.memberIds
      }
      else -> false
    }
  }

  fun canUpdateUser(before: UserProfile, after: UserProfile): Boolean {
    if (!isSelf(before.uid) || after.uid != before.uid) return false
    if (after.role != before.role) return false
    if (after.circleIds != before.circleIds) return false
    val affected = affectedUserKeys(before, after)
    return affected.all { it == "person" }
  }

  fun canDeleteUser(): Boolean = false

  fun canCreateCircle(resource: CareCircle): Boolean =
      isSignedIn() &&
          (isCaregiver() || isCaregiverAfter()) &&
          resource.id.isNotEmpty() &&
          resource.createdBy == authUid &&
          resource.memberIds == listOf(authUid) &&
          resource.patientId == ""

  fun canUpdateCircle(before: CareCircle, after: CareCircle): Boolean =
      memberCircleFieldEdit(before.id, before, after)

  fun canDeleteCircle(): Boolean = false

  fun canCreateMedia(circleId: String, resource: MediaItem): Boolean =
      isMember(circleId) &&
          resource.id.isNotEmpty() &&
          resource.authorId == authUid &&
          mediaStoragePathMatches(circleId, resource.storagePath)

  fun canUpdateMedia(circleId: String, before: MediaItem, after: MediaItem): Boolean =
      isMember(circleId) &&
          before.authorId == authUid &&
          after.id == before.id &&
          after.authorId == before.authorId &&
          mediaStoragePathMatches(circleId, after.storagePath)

  fun canDeleteMedia(circleId: String, before: MediaItem): Boolean =
      isMember(circleId) && before.authorId == authUid

  fun canWriteStory(circleId: String, resource: Story): Boolean =
      isMember(circleId) && resource.id.isNotEmpty()

  fun canDeleteStory(circleId: String): Boolean = isMember(circleId)

  fun canWriteAppointment(circleId: String, resource: Appointment): Boolean =
      isMember(circleId) && isCaregiver() && resource.id.isNotEmpty()

  fun canDeleteAppointment(circleId: String): Boolean = isMember(circleId) && isCaregiver()

  fun canGetInvitation(): Boolean = isSignedIn()

  fun canListInvitations(): Boolean = false

  fun canCreateInvitation(resource: Invitation): Boolean =
      isCaregiver() &&
          isMember(resource.circleId) &&
          resource.createdBy == authUid &&
          resource.usedBy == null

  @Suppress("UNUSED_PARAMETER")
  fun canUpdateInvitation(before: Invitation, after: Invitation): Boolean = false

  fun canDeleteInvitation(): Boolean = false

  private fun affectedUserKeys(before: UserProfile, after: UserProfile): Set<String> {
    val keys = mutableSetOf<String>()
    if (before.uid != after.uid) keys += "uid"
    if (before.role != after.role) keys += "role"
    if (before.person != after.person) keys += "person"
    if (before.circleIds != after.circleIds) keys += "circleIds"
    return keys
  }
}
