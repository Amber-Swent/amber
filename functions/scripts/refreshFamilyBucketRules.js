// Written by Alaabrine, with assistance from Cursor Agent.
/**
 * One-off: release the current `storage.rules` on every Amber-managed family bucket.
 *
 * Existing buckets only get rules when they are created
 * ([provisionCareCircleBucket](../src/provisionCareCircleBucket.js)). After changing
 * `functions/storage.rules`, run this so those buckets pick up the new ruleset.
 *
 * From `functions/`:
 *   node scripts/refreshFamilyBucketRules.js
 *
 * Requires Application Default Credentials (or `GOOGLE_APPLICATION_CREDENTIALS`) and a
 * project id (`GCLOUD_PROJECT` / `GCP_PROJECT` / Firebase app options).
 */
const { getStorage } = require("firebase-admin/storage");
const { ensureFamilyBucketRules } = require("../src/familyBucket");

// Initializes the Admin app used by familyBucket / getStorage.
require("../src/firebase");

/**
 * Finds every Cloud Storage bucket labelled `amber_managed=true` and releases the current
 * `storage.rules` on it via [ensureFamilyBucketRules].
 *
 * @returns {Promise<string[]>} the bucket names that were updated
 */
async function refreshAllFamilyBucketRules() {
  const [buckets] = await getStorage().getBuckets();
  const familyBucketNames = [];

  for (const bucket of buckets) {
    const [metadata] = await bucket.getMetadata();
    if (metadata.labels?.amber_managed === "true") {
      familyBucketNames.push(bucket.name);
    }
  }

  for (const bucketName of familyBucketNames) {
    console.log(`Releasing storage.rules on ${bucketName}`);
    await ensureFamilyBucketRules(bucketName);
  }

  console.log(`Updated ${familyBucketNames.length} bucket(s)`);
  return familyBucketNames;
}

if (require.main === module) {
  refreshAllFamilyBucketRules().catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
}

module.exports = { refreshAllFamilyBucketRules };
