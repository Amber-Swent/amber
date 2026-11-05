// Written by GitHub Copilot.
// Modified by Neha Chakraborty and Copilot, with assistance from
// Claude (Anthropic) via Claude Code.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
const { getStorage } = require("firebase-admin/storage");
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { db } = require("./firebase");
const {
  ensureFamilyBucket,
  ensureFamilyBucketRules,
  ensureFirebaseLinkedBucket,
} = require("./familyBucket");

/**
 * Gives a new circle its own bucket: creates it, links it to Firebase Storage, releases its rules,
 * then records its name in the circle's storageBucket. Retried on failure, so every step can run
 * again. If the circle was deleted meanwhile, its new bucket is deleted too.
 */
const provisionCareCircleBucket = onDocumentCreated(
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

module.exports = { provisionCareCircleBucket };
