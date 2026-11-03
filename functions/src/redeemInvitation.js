// Written by GitHub Copilot.
// Modified by Neha Chakraborty and Copilot, with assistance from
// Claude (Anthropic) via Claude Code.
// Modified by Neha Chakraborty, with assistance from
// Claude (Anthropic) via Claude Code.
const { FieldValue } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { db } = require("./firebase");

const VALID_ROLES = ["CAREGIVER", "PATIENT"];

// Clients map redemption failures on `details.reason`; messages are for logs only.
function redemptionFailed(reason, message) {
  return new HttpsError("failed-precondition", message, { reason });
}

function validInvitationCircleId(invitation) {
  if (typeof invitation.circleId !== "string" || invitation.circleId.length === 0) {
    throw redemptionFailed("CIRCLE_UNAVAILABLE", "Invitation has an invalid circle");
  }
  return invitation.circleId;
}

function validUser(userSnapshot) {
  if (!userSnapshot.exists) {
    throw redemptionFailed("PROFILE_INVALID", "User profile was not found");
  }
  const user = userSnapshot.data();
  if (!VALID_ROLES.includes(user.role)) {
    throw redemptionFailed("PROFILE_INVALID", "User profile has an invalid role");
  }
  if (user.deletionRequested === true) {
    throw redemptionFailed("PROFILE_INVALID", "User is being deleted");
  }
  return user;
}

async function readValidUser(transaction, userRef) {
  return validUser(await transaction.get(userRef));
}

const redeemInvitation = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in before redeeming an invitation");
  }

  const code = request.data?.code;
  if (
    !request.data
    || typeof request.data !== "object"
    || Array.isArray(request.data)
    || Object.keys(request.data).length !== 1
    || typeof code !== "string"
    || !/^[A-Za-z0-9_-]{1,128}$/.test(code)
  ) {
    throw new HttpsError("invalid-argument", "A valid invitation code is required");
  }

  const uid = request.auth.uid;
  const invitationRef = db.doc(`invitations/${code}`);
  const userRef = db.doc(`users/${uid}`);
  return db.runTransaction(async (transaction) => {
    const invitationSnapshot = await transaction.get(invitationRef);
    if (!invitationSnapshot.exists) {
      throw new HttpsError("not-found", "Invitation was not found");
    }

    const invitation = invitationSnapshot.data();
    if (invitation.usedBy === uid) {
      const circleId = validInvitationCircleId(invitation);
      const user = await readValidUser(transaction, userRef);
      return { circleId, role: user.role };
    }
    if (invitation.usedBy != null) {
      throw redemptionFailed("INVITATION_ALREADY_REDEEMED", "Invitation has already been redeemed");
    }
    if (Object.hasOwn(invitation, "role")) {
      throw redemptionFailed("INVITATION_INVALID", "Invitation must not specify a role");
    }

    const expirationMs = typeof invitation.expiresAt === "number"
      ? invitation.expiresAt
      : invitation.expiresAt?.toMillis?.();
    if (!Number.isFinite(expirationMs) || expirationMs <= Date.now()) {
      throw redemptionFailed("INVITATION_EXPIRED", "Invitation has expired");
    }
    const circleId = validInvitationCircleId(invitation);
    const circleRef = db.doc(`careCircles/${circleId}`);
    const [circleSnapshot, userSnapshot] = await Promise.all([
      transaction.get(circleRef),
      transaction.get(userRef),
    ]);
    if (!circleSnapshot.exists) {
      throw redemptionFailed("CIRCLE_UNAVAILABLE", "Invitation circle no longer exists");
    }

    const user = validUser(userSnapshot);
    if (!Array.isArray(user.circleIds)) {
      throw redemptionFailed("PROFILE_INVALID", "User profile has invalid circle membership");
    }

    const circle = circleSnapshot.data();
    if (!Array.isArray(circle.memberIds) || circle.memberIds.includes(uid)) {
      throw redemptionFailed("ALREADY_CIRCLE_MEMBER", "User is already a circle member");
    }
    if (user.role === "PATIENT" && circle.patientId) {
      throw redemptionFailed("PATIENT_SLOT_TAKEN", "Circle already has a patient");
    }

    transaction.update(invitationRef, { usedBy: uid });
    transaction.update(circleRef, {
      memberIds: FieldValue.arrayUnion(uid),
      ...(user.role === "PATIENT" ? { patientId: uid } : {}),
    });
    transaction.update(userRef, { circleIds: FieldValue.arrayUnion(circleId) });

    return { circleId, role: user.role };
  });
});

module.exports = { redeemInvitation };
