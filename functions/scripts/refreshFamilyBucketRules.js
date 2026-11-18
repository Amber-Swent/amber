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
 * `storage.rules` on it via [ensureFamilyBucketRules]. A bucket whose release fails is recorded and
 * the rest are still updated, so one failure does not leave every later circle on the old rules.
 *
 * `getBuckets()` already includes each bucket's metadata, labels included.
 *
 * @returns {Promise<{updated: string[], failed: string[]}>} the bucket names that were updated, and
 *   those whose release failed
 */
async function refreshAllFamilyBucketRules() {
  const [buckets] = await getStorage().getBuckets();
  const familyBuckets = buckets.filter(
    (bucket) => bucket.metadata?.labels?.amber_managed === "true",
  );
  const updated = [];
  const failed = [];

  for (const bucket of familyBuckets) {
    console.log(`Releasing storage.rules on ${bucket.name}`);
    try {
      await ensureFamilyBucketRules(bucket.name);
      updated.push(bucket.name);
    } catch (error) {
      console.error(`Failed to release storage.rules on ${bucket.name}:`, error);
      failed.push(bucket.name);
    }
  }

  if (failed.length === 0) {
    console.log(`Updated ${updated.length} bucket(s)`);
  } else {
    console.error(
      `Updated ${updated.length} bucket(s); failed ${failed.length}: ${failed.join(", ")}`,
    );
  }
  return {updated, failed};
}

/** Runs the refresh and sets a failing exit code if any bucket failed or the refresh threw. */
async function main() {
  try {
    const result = await refreshAllFamilyBucketRules();
    if (result.failed.length > 0) process.exitCode = 1;
  } catch (error) {
    console.error(error);
    process.exitCode = 1;
  }
}

if (require.main === module) main();

module.exports = {refreshAllFamilyBucketRules, main};
