// Written by GitHub Copilot.
// Modified by Neha Chakraborty and Copilot, with assistance from
// Claude (Anthropic) via Claude Code.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
const { createHash } = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { getSecurityRules } = require("firebase-admin/security-rules");
const { getStorage } = require("firebase-admin/storage");
const { defineString } = require("firebase-functions/params");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { onDocumentCreated } = require("firebase-functions/v2/firestore");

const app = initializeApp();

const bucketLocation = defineString("STORAGE_BUCKET_LOCATION");
const db = getFirestore();
const VALID_ROLES = ["CAREGIVER", "PATIENT"];
const RECENT_SIGN_IN_SECONDS = 5 * 60;
const FIRESTORE_NOT_FOUND = 5;
// Below Firestore's limit of 500 writes in one batch
const BATCH_SIZE = 400;

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

function bucketDetails(projectId, circleId) {
  const circleHash = createHash("sha256")
    .update(`${projectId}:${circleId}`)
    .digest("hex")
    .slice(0, 24);

  return {
    name: `amber-family-${circleHash}`,
    circleHash,
  };
}

async function ensureFamilyBucket(projectId, circleId) {
  const { name, circleHash } = bucketDetails(projectId, circleId);
  const bucket = getStorage().bucket(name);

  try {
    await bucket.create({
      location: bucketLocation.value(),
      iamConfiguration: {
        uniformBucketLevelAccess: {
          enabled: true,
        },
        publicAccessPrevention: "enforced",
      },
      labels: {
        amber_managed: "true",
        circle_hash: circleHash,
      },
    });
  } catch (error) {
    if (error.code !== 409) {
      throw error;
    }

    const [metadata] = await bucket.getMetadata();
    if (
      metadata.labels?.amber_managed !== "true"
      || metadata.labels?.circle_hash !== circleHash
    ) {
      throw new Error(`Refusing to reuse unowned bucket ${name}`);
    }
  }

  await bucket.setMetadata({
    iamConfiguration: {
      uniformBucketLevelAccess: {
        enabled: true,
      },
      publicAccessPrevention: "enforced",
    },
  });

  return name;
}

// The app reaches a bucket through Firebase Storage, which only serves the buckets linked to the
// project: a bucket just created in Cloud Storage isn't, until this links it.
async function ensureFirebaseLinkedBucket(projectId, bucketName) {
  const { access_token: accessToken } = await app.options.credential.getAccessToken();
  const response = await fetch(
    `https://firebasestorage.googleapis.com/v1beta/projects/${projectId}/buckets/${bucketName}:addFirebase`,
    {
      method: "POST",
      headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
      body: "{}",
    },
  );
  // 409: already linked by an earlier, retried run
  if (!response.ok && response.status !== 409) {
    throw new Error(
      `Could not link bucket ${bucketName} to Firebase: ${response.status} ${await response.text()}`,
    );
  }
}

async function ensureFamilyBucketRules(bucketName) {
  const securityRules = getSecurityRules();
  const source = fs.readFileSync(path.join(__dirname, "storage.rules"), "utf8");
  let ruleset;

  try {
    ruleset = await securityRules.getStorageRuleset(bucketName);
    const currentSource = ruleset.source.find((file) => file.name === "storage.rules");
    if (currentSource?.content !== source) {
      ruleset = await securityRules.createRuleset(
        securityRules.createRulesFileFromSource("storage.rules", source),
      );
    }
  } catch (error) {
    // firebase-admin prefixes its error codes: a bucket without rules yet gives this one
    if (error.code !== "security-rules/not-found") {
      throw error;
    }
    ruleset = await securityRules.createRuleset(
      securityRules.createRulesFileFromSource("storage.rules", source),
    );
  }

  await securityRules.releaseStorageRuleset(ruleset, bucketName);
}

exports.redeemInvitation = onCall(async (request) => {
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

// The Admin SDK's deleteUser doesn't check this itself. Clients map failures on `details.reason`.
function requireRecentSignIn(token) {
  const signedInSeconds = token?.auth_time;
  if (
    !Number.isFinite(signedInSeconds)
    || Date.now() / 1000 - signedInSeconds > RECENT_SIGN_IN_SECONDS
  ) {
    throw new HttpsError("failed-precondition", "Sign in again before deleting your account", {
      reason: "RECENT_SIGN_IN_REQUIRED",
    });
  }
}

async function writeInBatches(items, write) {
  for (let start = 0; start < items.length; start += BATCH_SIZE) {
    const batch = db.batch();
    items.slice(start, start + BATCH_SIZE).forEach((item) => write(batch, item));
    await batch.commit();
  }
}

async function deleteDocuments(snapshots) {
  await writeInBatches(snapshots, (batch, snapshot) => batch.delete(snapshot.ref));
}

// Comes first: redeemInvitation reads the profile in its transaction, so once this is written no
// redemption can add a circle that the query in deleteAccount would miss. Stays set after a partial
// failure, as the account is then half deleted. The profile is already gone on some retries.
async function markDeletionRequested(uid) {
  try {
    await db.doc(`users/${uid}`).update({ deletionRequested: true });
  } catch (error) {
    if (error.code !== FIRESTORE_NOT_FOUND) {
      throw error;
    }
  }
}

async function deleteInvitations(uid) {
  const invitations = db.collection("invitations");
  const [created, used] = await Promise.all([
    invitations.where("createdBy", "==", uid).get(),
    invitations.where("usedBy", "==", uid).get(),
  ]);
  await deleteDocuments([...created.docs, ...used.docs]);
}

// Stories go first: the ids to remove are only known while the media documents still exist.
async function deleteAuthoredMedia(uid, circleRef, bucketName) {
  const media = await circleRef.collection("media").where("authorId", "==", uid).get();
  const stories = await circleRef.collection("stories").get();
  const mediaIds = new Set(media.docs.map((item) => item.id));

  const ownStories = stories.docs.filter((story) => story.get("createdBy") === uid);
  await deleteDocuments(ownStories);
  const otherStories = stories.docs
    .filter((story) => story.get("createdBy") !== uid)
    .map((story) => ({
      ref: story.ref,
      ids: (story.get("mediaIds") ?? []).filter((id) => mediaIds.has(id)),
    }))
    .filter((story) => story.ids.length > 0);
  await writeInBatches(otherStories, (batch, story) =>
    batch.update(story.ref, { mediaIds: FieldValue.arrayRemove(...story.ids) }));

  // The files go before their documents, the only record of where they are. Without a bucket
  // (always the case in the emulator) no file can have been stored.
  if (bucketName) {
    const bucket = getStorage().bucket(bucketName);
    for (const item of media.docs) {
      const storagePath = item.get("storagePath");
      if (storagePath) {
        await bucket.file(storagePath).delete({ ignoreNotFound: true });
      }
    }
  }
  await deleteDocuments(media.docs);
}

async function removeFromRemainingMedia(uid, circleRef) {
  const media = circleRef.collection("media");
  const [shown, authorized] = await Promise.all([
    media.where("shownIds", "array-contains", uid).get(),
    media.where("authorizedIds", "array-contains", uid).get(),
  ]);
  await writeInBatches(shown.docs, (batch, item) =>
    batch.update(item.ref, { shownIds: FieldValue.arrayRemove(uid) }));
  // An empty list means the whole circle can see the media: keep it restricted to its author
  await writeInBatches(authorized.docs, (batch, item) => {
    const isOnlyOneAllowed = item.get("authorizedIds").length === 1;
    batch.update(item.ref, {
      authorizedIds: isOnlyOneAllowed ? [item.get("authorId")] : FieldValue.arrayRemove(uid),
    });
  });
}

async function removeFromAppointments(uid, circleRef) {
  const appointments = circleRef.collection("appointments");
  const [attending, created] = await Promise.all([
    appointments.where("attendeeIds", "array-contains", uid).get(),
    appointments.where("createdBy", "==", uid).get(),
  ]);
  await writeInBatches(attending.docs, (batch, appointment) =>
    batch.update(appointment.ref, { attendeeIds: FieldValue.arrayRemove(uid) }));
  await writeInBatches(created.docs, (batch, appointment) =>
    batch.update(appointment.ref, { createdBy: "" }));
}

function nicknamesWithout(uid, nicknames) {
  return Object.fromEntries(
    Object.entries(nicknames)
      .filter(([viewerId]) => viewerId !== uid)
      .map(([viewerId, viewerNicknames]) => [
        viewerId,
        Object.fromEntries(Object.entries(viewerNicknames).filter(([id]) => id !== uid)),
      ]),
  );
}

// Removes the membership, unless it's the last one: the circle is then deleted as a whole.
// Returns whether it was the last one.
async function leaveCircle(uid, circleRef) {
  return db.runTransaction(async (transaction) => {
    const circleSnapshot = await transaction.get(circleRef);
    // a double tap: another call deleted it first
    if (!circleSnapshot.exists) {
      return false;
    }
    const circle = circleSnapshot.data();
    if (circle.memberIds.every((memberId) => memberId === uid)) {
      return true;
    }
    transaction.update(circleRef, {
      memberIds: FieldValue.arrayRemove(uid),
      nicknames: nicknamesWithout(uid, circle.nicknames ?? {}),
      ...(circle.patientId === uid ? { patientId: "" } : {}),
      ...(circle.createdBy === uid ? { createdBy: "" } : {}),
    });
    return false;
  });
}

// The emulators don't provision buckets, and this would reach the real Cloud Storage.
async function deleteBucket(bucketName) {
  if (!bucketName || process.env.FUNCTIONS_EMULATOR === "true") {
    return;
  }
  const bucket = getStorage().bucket(bucketName);
  try {
    await bucket.deleteFiles({ force: true });
  } catch (error) {
    // 404: deleted by an earlier, retried run
    if (error.code !== 404) {
      throw error;
    }
  }
  await bucket.delete({ ignoreNotFound: true });
}

// A retry finds the circle through memberIds, so the uid must leave it last. The bucket goes before
// the circle for the same reason: its name would be lost with the document.
async function removeFromCircle(uid, circleSnapshot) {
  const circleRef = circleSnapshot.ref;
  const bucketName = circleSnapshot.get("storageBucket");
  await deleteAuthoredMedia(uid, circleRef, bucketName);
  await removeFromRemainingMedia(uid, circleRef);
  await removeFromAppointments(uid, circleRef);
  if (await leaveCircle(uid, circleRef)) {
    await deleteBucket(bucketName);
    await db.recursiveDelete(circleRef);
  }
}

async function deleteAuthUser(uid) {
  try {
    await getAuth().deleteUser(uid);
  } catch (error) {
    // already deleted by an earlier, retried run
    if (error.code !== "auth/user-not-found") {
      throw error;
    }
  }
}

// Permanently deletes the caller's account and personal data. Every step can run again, so a client
// can simply retry after a failure. Only ever acts on request.auth.uid: the request data is ignored.
exports.deleteAccount = onCall({ timeoutSeconds: 540 }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in before deleting your account");
  }
  requireRecentSignIn(request.auth.token);

  const uid = request.auth.uid;
  await markDeletionRequested(uid);
  // Before the circles, so nobody joins a circle that is about to be deleted
  await deleteInvitations(uid);
  const circles = await db.collection("careCircles").where("memberIds", "array-contains", uid).get();
  for (const circle of circles.docs) {
    await removeFromCircle(uid, circle);
  }
  await db.doc(`users/${uid}`).delete();
  await deleteAuthUser(uid);
  return { deleted: true };
});

exports.provisionCareCircleBucket = onDocumentCreated(
  {
    document: "careCircles/{circleId}",
    retry: true,
  },
  async (event) => {
    // The emulators can't create buckets or release Storage rules, so provisioning there would call
    // the real Cloud Storage and Security Rules APIs. Circles created there keep an empty
    // storageBucket.
    if (process.env.FUNCTIONS_EMULATOR === "true") {
      return;
    }

    const circleId = event.params.circleId;
    const projectId = process.env.GCLOUD_PROJECT;
    if (!projectId) {
      throw new Error("GCLOUD_PROJECT is not available");
    }

    const circleRef = db.doc(`careCircles/${circleId}`);
    const circleSnapshot = await circleRef.get();
    if (!circleSnapshot.exists) {
      return;
    }
    if (circleSnapshot.get("storageBucket")) {
      return;
    }

    const storageBucket = await ensureFamilyBucket(projectId, circleId);
    await ensureFirebaseLinkedBucket(projectId, storageBucket);
    await ensureFamilyBucketRules(storageBucket);
    const latestCircle = await circleRef.get();
    if (!latestCircle.exists) {
      await getStorage().bucket(storageBucket).delete({ ignoreNotFound: true });
      return;
    }

    if (!latestCircle.get("storageBucket")) {
      await circleRef.update({ storageBucket });
    }
  },
);
