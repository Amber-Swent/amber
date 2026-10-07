// Written by GitHub Copilot.
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

initializeApp();

const bucketLocation = defineString("STORAGE_BUCKET_LOCATION");
const db = getFirestore();

function newUserPerson(value, uid) {
  const person = value ?? {};
  if (typeof person !== "object" || Array.isArray(person)) {
    throw new HttpsError("invalid-argument", "Person must be an object");
  }
  if (Object.keys(person).some((key) => !["firstName", "lastName", "nickname"].includes(key))) {
    throw new HttpsError("invalid-argument", "Person contains unsupported fields");
  }
  if (
    (person.firstName !== undefined && typeof person.firstName !== "string")
    || (person.lastName !== undefined && typeof person.lastName !== "string")
    || (person.nickname !== undefined
      && person.nickname !== null
      && typeof person.nickname !== "string")
  ) {
    throw new HttpsError("invalid-argument", "Person fields have invalid types");
  }

  return {
    id: uid,
    firstName: person.firstName ?? "",
    lastName: person.lastName ?? "",
    nickname: person.nickname ?? null,
  };
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
    if (error.code !== "not-found") {
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
  if (typeof code !== "string" || !/^[A-Za-z0-9_-]{1,128}$/.test(code)) {
    throw new HttpsError("invalid-argument", "A valid invitation code is required");
  }

  const uid = request.auth.uid;
  const invitationRef = db.doc(`invitations/${code}`);
  return db.runTransaction(async (transaction) => {
    const invitationSnapshot = await transaction.get(invitationRef);
    if (!invitationSnapshot.exists) {
      throw new HttpsError("not-found", "Invitation was not found");
    }

    const invitation = invitationSnapshot.data();
    if (invitation.usedBy === uid) {
      return { circleId: invitation.circleId, role: invitation.role };
    }
    if (invitation.usedBy != null) {
      throw new HttpsError("failed-precondition", "Invitation has already been redeemed");
    }
    if (!["CAREGIVER", "PATIENT"].includes(invitation.role)) {
      throw new HttpsError("failed-precondition", "Invitation has an invalid role");
    }

    const expirationMs = typeof invitation.expiresAt === "number"
      ? invitation.expiresAt
      : invitation.expiresAt?.toMillis?.();
    if (!Number.isFinite(expirationMs) || expirationMs <= Date.now()) {
      throw new HttpsError("failed-precondition", "Invitation has expired");
    }

    if (typeof invitation.circleId !== "string" || invitation.circleId.length === 0) {
      throw new HttpsError("failed-precondition", "Invitation has an invalid circle");
    }
    const circleRef = db.doc(`careCircles/${invitation.circleId}`);
    const userRef = db.doc(`users/${uid}`);
    const [circleSnapshot, userSnapshot] = await Promise.all([
      transaction.get(circleRef),
      transaction.get(userRef),
    ]);
    if (!circleSnapshot.exists) {
      throw new HttpsError("failed-precondition", "Invitation circle no longer exists");
    }

    const circle = circleSnapshot.data();
    if (!Array.isArray(circle.memberIds) || circle.memberIds.includes(uid)) {
      throw new HttpsError("failed-precondition", "User is already a circle member");
    }
    if (invitation.role === "PATIENT" && circle.patientId) {
      throw new HttpsError("failed-precondition", "Circle already has a patient");
    }

    const user = userSnapshot.data();
    if (userSnapshot.exists && user.role !== invitation.role) {
      throw new HttpsError("failed-precondition", "Invitation role does not match user profile");
    }
    if (userSnapshot.exists && !Array.isArray(user.circleIds)) {
      throw new HttpsError("failed-precondition", "User profile has invalid circle membership");
    }

    transaction.update(invitationRef, { usedBy: uid });
    transaction.update(circleRef, {
      memberIds: FieldValue.arrayUnion(uid),
      ...(invitation.role === "PATIENT" ? { patientId: uid } : {}),
    });
    if (userSnapshot.exists) {
      transaction.update(userRef, { circleIds: FieldValue.arrayUnion(invitation.circleId) });
    } else {
      transaction.create(userRef, {
        uid,
        role: invitation.role,
        person: newUserPerson(request.data?.person, uid),
        circleIds: [invitation.circleId],
      });
    }

    return { circleId: invitation.circleId, role: invitation.role };
  });
});

exports.provisionCareCircleBucket = onDocumentCreated(
  {
    document: "careCircles/{circleId}",
    retry: true,
  },
  async (event) => {
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
