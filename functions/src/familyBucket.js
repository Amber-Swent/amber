// Written by GitHub Copilot.
// Modified by Neha Chakraborty and Copilot, with assistance from
// Claude (Anthropic) via Claude Code.
// Modified by Neha Chakraborty, with assistance from
// Claude (Anthropic) via Claude Code.
const { createHash } = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const { getSecurityRules } = require("firebase-admin/security-rules");
const { getStorage } = require("firebase-admin/storage");
const { defineString } = require("firebase-functions/params");
const { app } = require("./firebase");

const bucketLocation = defineString("STORAGE_BUCKET_LOCATION");

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
  const source = fs.readFileSync(path.join(__dirname, "..", "storage.rules"), "utf8");
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

/**
 * Tells whether a circle's files are to be deleted from Cloud Storage. Without a bucket no file
 * can have been stored. The emulators don't provision buckets, and this would reach the real Cloud
 * Storage.
 * @param {string | undefined} bucketName the circle's storageBucket
 * @returns {boolean} true if there is a bucket and this isn't the emulator
 */
function usesStorage(bucketName) {
  return Boolean(bucketName) && process.env.FUNCTIONS_EMULATOR !== "true";
}

/**
 * Deletes a circle's bucket with all its files. Does nothing when usesStorage is false.
 * @param {string | undefined} bucketName the circle's storageBucket
 */
async function deleteBucket(bucketName) {
  if (!usesStorage(bucketName)) {
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

module.exports = { bucketDetails, ensureFamilyBucket, ensureFirebaseLinkedBucket, ensureFamilyBucketRules, usesStorage, deleteBucket };
