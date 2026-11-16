// Written by Alaabrine, with assistance from Cursor Agent.
const assert = require("node:assert/strict");
const {spawnSync} = require("node:child_process");
const fs = require("node:fs");
const Module = require("node:module");
const os = require("node:os");
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

/**
 * Runs `refreshFamilyBucketRules.js` as the main module, so its `process.exitCode` lines run.
 * [mode] is `ok`, `failed-bucket` (the release throws), or `list-throws` (listing buckets throws).
 * @param {"ok" | "failed-bucket" | "list-throws"} mode
 */
function runAsMain(mode) {
  const preload = path.join(os.tmpdir(), `amber-refresh-preload-${process.pid}-${Date.now()}.js`);
  fs.writeFileSync(
    preload,
    `
const Module = require("node:module");
const original = Module._load;
const mode = process.env.AMBER_REFRESH_TEST;
Module._load = function (request, parent, isMain) {
  if (request === "firebase-admin/storage") {
    return {
      getStorage: () => ({
        getBuckets: async () => {
          if (mode === "list-throws") throw new Error("list failed");
          return [[{name: "family-a", metadata: {labels: {amber_managed: "true"}}}]];
        },
      }),
    };
  }
  if (request === "../src/familyBucket") {
    return {
      ensureFamilyBucketRules: async () => {
        if (mode === "failed-bucket") throw new Error("rules unavailable");
      },
    };
  }
  if (request === "../src/firebase") return {};
  return original.apply(this, arguments);
};
`,
  );
  try {
    return spawnSync(
      process.execPath,
      ["--require", preload, path.join(__dirname, "..", "scripts", "refreshFamilyBucketRules.js")],
      {encoding: "utf8", timeout: 15_000, env: {...process.env, AMBER_REFRESH_TEST: mode}},
    );
  } finally {
    fs.unlinkSync(preload);
  }
}

test("a successful rules release exits with code 0", () => {
  const result = runAsMain("ok");

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Updated 1 bucket/);
});

test("a failed rules release exits with code 1", () => {
  const result = runAsMain("failed-bucket");

  assert.match(result.stderr, /failed 1: family-a/);
  assert.equal(result.status, 1);
});

test("a failure listing buckets exits with code 1", () => {
  const result = runAsMain("list-throws");

  assert.match(result.stderr, /list failed/);
  assert.equal(result.status, 1);
});
