// Written by GitHub Copilot.
// Modified by Neha Chakraborty and Copilot, with assistance from
// Claude (Anthropic) via Claude Code.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
const { createHash } = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const { initializeApp } = require("firebase-admin/app");
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
