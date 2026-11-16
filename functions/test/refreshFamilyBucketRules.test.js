// Written by Alaabrine, with assistance from Cursor Agent.
const assert = require("node:assert/strict");
const Module = require("node:module");
const path = require("node:path");
const test = require("node:test");

/**
 * Loads the one-off script with Storage and rules release replaced, so the test never talks to
 * Cloud Storage. [buckets] are what getBuckets returns; [release] stands in for
 * ensureFamilyBucketRules.
 * @param {object[]} buckets
 * @param {(bucketName: string) => Promise<void>} [release]
 */
function loadScript(buckets, release = async () => {}) {
  const originalLoad = Module._load;
  Module._load = function mockModuleLoad(request, parent, isMain) {
    if (request === "firebase-admin/storage") {
      return {getStorage: () => ({getBuckets: async () => [buckets]})};
    }
    if (request === "../src/familyBucket") {
      return {ensureFamilyBucketRules: release};
    }
    if (request === "../src/firebase") return {};
    return originalLoad.call(this, request, parent, isMain);
  };

  const scriptPath = path.join(__dirname, "..", "scripts", "refreshFamilyBucketRules.js");
  delete require.cache[scriptPath];
  try {
    return require(scriptPath);
  } finally {
    Module._load = originalLoad;
  }
}

/** A bucket listed with [labels], or with no labels when [labels] is undefined. */
function bucket(name, labels) {
  return {
    name,
    metadata: labels === undefined ? {} : {labels},
  };
}

test("releases the current rules only on buckets labelled amber_managed", async () => {
  const released = [];
  const script = loadScript(
    [
      bucket("family-a", {amber_managed: "true"}),
      bucket("other", {amber_managed: "false"}),
      bucket("plain"),
      bucket("family-b", {amber_managed: "true", circle_hash: "abc"}),
    ],
    async (bucketName) => {
      released.push(bucketName);
    },
  );

  const result = await script.refreshAllFamilyBucketRules();

  assert.deepEqual(result, {updated: ["family-a", "family-b"], failed: []});
  assert.deepEqual(released, ["family-a", "family-b"]);
});

test("updates nothing when no bucket is Amber-managed", async () => {
  const script = loadScript([bucket("plain"), bucket("other", {amber_managed: "false"})]);

  assert.deepEqual(await script.refreshAllFamilyBucketRules(), {updated: [], failed: []});
});

test("a rules release failure is reported and the other buckets are still updated", async () => {
  const released = [];
  const script = loadScript(
    [
      bucket("family-a", {amber_managed: "true"}),
      bucket("family-b", {amber_managed: "true"}),
    ],
    async (bucketName) => {
      released.push(bucketName);
      if (bucketName === "family-a") throw new Error("rules unavailable");
    },
  );

  const result = await script.refreshAllFamilyBucketRules();

  assert.deepEqual(released, ["family-a", "family-b"]);
  assert.deepEqual(result, {updated: ["family-b"], failed: ["family-a"]});
});
