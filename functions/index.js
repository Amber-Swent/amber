// Written by GitHub Copilot.
const {initializeApp} = require("firebase-admin/app");
const {FieldValue, getFirestore} = require("firebase-admin/firestore");
const {onCall, HttpsError} = require("firebase-functions/v2/https");

initializeApp();

const db = getFirestore();

/** Returns the caller UID or rejects unauthenticated callable requests. */
function authenticatedUid(request) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in before performing this action.");
  }

  return request.auth.uid;
}

/** Validates identifiers and human-entered names before they become document fields or paths. */
function requiredString(value, fieldName) {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new HttpsError("invalid-argument", `${fieldName} must be a non-empty string.`);
  }

  return value.trim();
}

/** Builds the required embedded Person value when onboarding creates a profile server-side. */
function defaultPerson(uid) {
  return {
    id: uid,
    firstName: "",
    lastName: "",
    nickname: null,
  };
}

/**
 * Creates a caregiver's first care circle and profile, or adds a new circle ID to their existing
 * profile. The transaction keeps CareCircle.memberIds and UserProfile.circleIds synchronized.
 */
exports.createCareCircle = onCall(async (request) => {
  const uid = authenticatedUid(request);
  const circleId = requiredString(request.data?.circleId, "circleId");
  const name = requiredString(request.data?.name, "name");
  const profileRef = db.doc(`users/${uid}`);
  const circleRef = db.doc(`careCircles/${circleId}`);

  await db.runTransaction(async (transaction) => {
    const [profileSnapshot, circleSnapshot] = await Promise.all([
      transaction.get(profileRef),
      transaction.get(circleRef),
    ]);
    if (circleSnapshot.exists) {
      throw new HttpsError("already-exists", "A care circle with this ID already exists.");
    }

    if (profileSnapshot.exists && profileSnapshot.data().role !== "CAREGIVER") {
      throw new HttpsError(
          "permission-denied",
          "Only users with the CAREGIVER role can create care circles.",
      );
    }

    transaction.create(circleRef, {
      id: circleId,
      name,
      patientId: "",
      memberIds: [uid],
      people: {},
      nicknames: {},
      places: {},
      createdBy: uid,
      createdAt: Date.now(),
    });

    if (profileSnapshot.exists) {
      transaction.update(profileRef, {circleIds: FieldValue.arrayUnion(circleId)});
    } else {
      transaction.create(profileRef, {
        uid,
        role: "CAREGIVER",
        person: defaultPerson(uid),
        circleIds: [circleId],
      });
    }
  });

  return {circleId};
});

/**
 * Redeems a known invitation code exactly once. The transaction consumes the invitation, adds the
 * caller to the circle, links a patient when applicable, and creates or updates their profile.
 */
exports.redeemInvitation = onCall(async (request) => {
  const uid = authenticatedUid(request);
  const code = requiredString(request.data?.code, "code");
  const invitationRef = db.doc(`invitations/${code}`);

  return db.runTransaction(async (transaction) => {
    const invitationSnapshot = await transaction.get(invitationRef);
    if (!invitationSnapshot.exists) {
      throw new HttpsError("not-found", "Invitation not found.");
    }

    // Validate persisted invitation data too because Admin SDK writes bypass Firestore rules.
    const invitation = invitationSnapshot.data();
    if (invitation.usedBy !== null || invitation.expiresAt <= Date.now()) {
      throw new HttpsError("failed-precondition", "Invitation is no longer valid.");
    }
    if (invitation.role !== "CAREGIVER" && invitation.role !== "PATIENT") {
      throw new HttpsError("failed-precondition", "Invitation has an unsupported role.");
    }

    const circleRef = db.doc(`careCircles/${invitation.circleId}`);
    const profileRef = db.doc(`users/${uid}`);
    const [circleSnapshot, profileSnapshot] = await Promise.all([
      transaction.get(circleRef),
      transaction.get(profileRef),
    ]);
    if (!circleSnapshot.exists) {
      throw new HttpsError("not-found", "Care circle not found.");
    }

    const circle = circleSnapshot.data();
    if (profileSnapshot.exists && profileSnapshot.data().role !== invitation.role) {
      throw new HttpsError(
          "failed-precondition",
          "The invitation role does not match the existing user profile.",
      );
    }
    if (invitation.role === "PATIENT"
        && circle.patientId !== ""
        && circle.patientId !== uid) {
      throw new HttpsError(
          "failed-precondition",
          "This care circle already has a linked patient.",
      );
    }

    // Commit all related documents together so a used code can never leave partial membership.
    transaction.update(invitationRef, {usedBy: uid});
    transaction.update(circleRef, {
      memberIds: FieldValue.arrayUnion(uid),
      ...(invitation.role === "PATIENT" ? {patientId: uid} : {}),
    });
    if (profileSnapshot.exists) {
      transaction.update(profileRef, {
        circleIds: FieldValue.arrayUnion(invitation.circleId),
      });
    } else {
      transaction.create(profileRef, {
        uid,
        role: invitation.role,
        person: defaultPerson(uid),
        circleIds: [invitation.circleId],
      });
    }

    return {circleId: invitation.circleId};
  });
});
